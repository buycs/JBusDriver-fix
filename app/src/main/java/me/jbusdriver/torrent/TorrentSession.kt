package me.jbusdriver.torrent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import me.jbusdriver.BuildConfig
import org.libtorrent4j.AlertListener
import org.libtorrent4j.FileStorage
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataFailedAlert
import org.libtorrent4j.alerts.TorrentErrorAlert
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.settings_pack
import org.libtorrent4j.swig.string_vector
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * 磁力在线播放用的 BT 会话（进程内单例）。
 *
 * ## 为什么播「本地增长文件」而不是自定义 piece DataSource
 *
 * 原方案准备实现一个 Media3 `DataSource`，用 `readPiece()` + `ReadPieceAlert` 把分片字节
 * 直接喂给 ExoPlayer。**这条路在本依赖上走不通**：libtorrent4j `2.1.0-39` 的
 * `ReadPieceAlert` 只有 `error()/piece()/size()/bufferPtr()`，缓冲区是个裸指针 `long`
 * （`read_piece_alert.buffer_ptr()`），Java 侧没有安全解引用的手段（只能上 `Unsafe`）。
 *
 * 于是取成熟产品的实际形态：引擎把主视频写进磁盘，播放侧读这个正在增长的文件，读到哪、
 * 引擎补到哪。但**必须按分片粒度判定可读范围**，不能只看「连续前缀」：
 *
 * - 文件一创建就是全长（`SparseFiles` 语义下尾部是空洞，读出来是 0 字节），不钳住就等于
 *   让 ExoPlayer 把空洞当数据解析。
 * - 尾窗口（见下）的分片会**早于**连续前缀到位，如果只认前缀，mp4 的尾部 moov 永远读不到，
 *   表现就是一直转圈。
 *
 * ## 调度策略（迅雷式，全部是引擎侧能力，不依赖任何远端服务）
 *
 * - **头窗口**：`SEQUENTIAL_DOWNLOAD` + 起始分片 `TOP_PRIORITY` → 保证立刻能开播。
 * - **尾窗口**：文件末尾分片 `TOP_PRIORITY` → 救非 faststart 的 mp4（moov 在尾部，
 *   纯顺序下载要等到最后一片才拿得到）。
 * - **跟随播放位置**：读到哪就给哪段分片设 deadline，让引擎按播放进度重排请求。
 * - **端口打通**：UPnP + NAT-PMP + LSD + DHT，`firewalled` 透出到 UI —— 被墙时做种者
 *   连不进来，是本地 BT 最大的命中率杀手。
 * - **多来源**：磁力串自带的 `tr=` 一个不丢（见 [me.jbusdriver.data.model.MagnetLink]），
 *   再补公共 tracker，所有 tier 都宣告。
 * - **握手加密**：`in/out_enc_policy = pe_enabled`，规避家宽/运营商对明文 BT 流量的干扰。
 */
class TorrentSession private constructor(context: Context) {

    /**
     * ⚠️ 构造参数 `true` 是 **logging**，不是「是否启动会话」——
     * 会话必须显式 [start]；在那之前 `session` 是 null，`settings()` 会返回 null。
     */
    private val manager = SessionManager(true)
    private val startLock = Any()

    @Volatile
    private var started = false

    /** 播放用暂存目录：每个任务一个子目录，退出播放即回收。 */
    private val mediaDir: File = File(context.cacheDir, "magnet_media").apply { mkdirs() }

    companion object {
        private const val TAG = "TorrentSession"

        /** 公共 tracker：磁力串里没带 `tr=` 时兜底。 */
        private val PUBLIC_TRACKERS = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://open.stealth.si:80/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.dler.org:6969/announce"
        )

        @Volatile
        private var instance: TorrentSession? = null

        fun get(context: Context): TorrentSession =
            instance ?: synchronized(this) {
                instance ?: TorrentSession(context.applicationContext).also { instance = it }
            }
    }

    /** 会话级连接状态，供 UI 解释「为什么这么慢」。 */
    data class NetworkState(val firewalled: Boolean = false, val dhtNodes: Long = 0L)

    @Volatile
    var networkState: NetworkState = NetworkState()
        private set

    @Volatile
    var lastAlertText: String? = null
        private set

    private val alertListener = object : AlertListener {
        // 只订这两种：一次播放会刷出成千上万条告警，全订等于自己把自己刷爆。
        override fun types(): IntArray = intArrayOf(
            AlertType.METADATA_FAILED.swig(),
            AlertType.TORRENT_ERROR.swig()
        )

        override fun alert(alert: Alert<*>) {
            when (alert) {
                is MetadataFailedAlert -> {
                    lastAlertText = "拿不到种子信息：${alert.getError()}"
                    log("告警 $lastAlertText")
                }
                is TorrentErrorAlert -> {
                    lastAlertText = "种子错误：${alert.error()}"
                    log("告警 $lastAlertText")
                }
            }
        }
    }

    /** 会话是单例，重复调用只会真正启动一次。 */
    fun start() {
        synchronized(startLock) {
            if (started) return
            manager.addListener(alertListener)
            // ⚠️ 顺序不能反：`SessionManager(boolean)` 的参数是 **logging**，不是「是否启动会话」
            // （反编译字节码：构造里 `iload_1; putfield logging:Z`，压根不碰 session）。
            // 没 start 之前 `session == null`，`settings()` 直接返回 null（`ifnull → aconst_null`），
            // Kotlin 的非空断言就抛 `settings(...) must not be null`。
            // 症状是「一播磁力立刻失败」，而探针工具（MagnetProbeEngine）因为顺序写对了反而正常。
            manager.start()
            applyTuning()
            runCatching { manager.startDht() }
            started = true
            refreshNetworkState()
            log("会话已启动 firewalled=${networkState.firewalled}")
        }
    }

    /**
     * 会话配置。
     *
     * ⚠️ 这里每个 setting 名都在 `libtorrent4j-2.1.0-39` 的 `settings_pack.int_types` /
     * `bool_types` 枚举里逐个核对过。**名字写错不会报错，只会被 libtorrent 静默忽略**，
     * 所以以后加配置项必须先去枚举里确认，不要凭 libtorrent C++ 文档的记忆写。
     */
    private fun applyTuning() {
        val pack: SettingsPack = manager.settings()

        // 让本机可达：被墙时做种者连不进来，命中率直接塌。
        pack.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)

        // 握手加密：家宽/运营商干扰明文 BT 流量的情况很常见。
        pack.setInteger(
            settings_pack.int_types.in_enc_policy.swigValue(),
            settings_pack.enc_policy.pe_enabled.swigValue()
        )
        pack.setInteger(
            settings_pack.int_types.out_enc_policy.swigValue(),
            settings_pack.enc_policy.pe_enabled.swigValue()
        )

        // 多来源宣告：所有 tier / tracker 都发，不要只挑第一个。
        pack.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.prefer_udp_trackers.swigValue(), true)

        // 播放期调度：要「能立刻开播」，不要「最快下完」。
        pack.setBoolean(settings_pack.bool_types.auto_sequential.swigValue(), true)
        pack.setBoolean(settings_pack.bool_types.prioritize_partial_pieces.swigValue(), true)
        // 一次只播一条，队列压到 1，免得带宽被排队的历史任务分走。
        pack.setInteger(settings_pack.int_types.active_downloads.swigValue(), 1)
        pack.setInteger(settings_pack.int_types.active_limit.swigValue(), 1)
        pack.setInteger(settings_pack.int_types.connections_limit.swigValue(), 200)
        pack.setInteger(settings_pack.int_types.max_out_request_queue.swigValue(), 500)
        pack.setInteger(settings_pack.int_types.send_buffer_watermark.swigValue(), 1_500_000)

        manager.applySettings(pack)
    }

    /** 刷新连接状态；起播前和卡住时各调一次，用于把「没人做种」和「端口被墙」区分开。 */
    fun refreshNetworkState() {
        networkState = NetworkState(
            firewalled = runCatching { manager.isFirewalled }.getOrDefault(false),
            dhtNodes = runCatching { manager.dhtNodes() }.getOrDefault(0L)
        )
    }

    /** 建一条磁力流；调用方必须 `close()`，否则上一条会继续下载抢带宽。 */
    fun open(magnet: String, tag: String): MagnetStream {
        start()
        refreshNetworkState()
        val dir = File(mediaDir, sanitize(tag)).apply { mkdirs() }
        return MagnetStream(this, manager, dir, magnet)
    }

    internal fun log(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    internal fun trackersFor(magnet: String): string_vector {
        val merged = LinkedHashSet<String>()
        // 站点磁力串里的 tr= 可能有好几个，也可能被 URL 编码过。
        magnet.split("&").forEach { part ->
            if (part.startsWith("tr=")) {
                val value = part.removePrefix("tr=")
                merged.add(runCatching { URLDecoder.decode(value, "UTF-8") }.getOrElse { value })
            }
        }
        merged.addAll(PUBLIC_TRACKERS)
        return string_vector(merged.toList())
    }

    private fun sanitize(tag: String): String =
        tag.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifBlank { "task" }
}

/** 判「这条资源在线播不了」的原因，UI 按它出准确文案。 */
sealed class MagnetUnavailable(override val message: String) : Exception(message) {

    /** 磁力串本身坏了（`parse_magnet_uri` 失败）。 */
    class BadMagnet(message: String) : MagnetUnavailable(message)

    /** 时限内没拿到种子信息：没有做种者，或者端口被墙连不上对端。 */
    class NoMetadata(message: String) : MagnetUnavailable(message)

    /** 种子是有的，但里面没有视频文件（比如只给了图片/说明书包）。 */
    class NoVideoFile(message: String) : MagnetUnavailable(message)

    /** 下载停滞：连上了对端但一段时间不推进，多半是假种/吸血源。 */
    class Stalled(message: String) : MagnetUnavailable(message)

    /**
     * 该设备架构没有 libtorrent4j 的原生库。
     *
     * 上游 2.1.0-39 只发 arm64-v8a / armeabi-v7a / x86_64 三枚 .so，**没有 32 位 x86**。
     * 缺库时 `SessionManager` 的类初始化抛 `UnsatisfiedLinkError`（属于 `Error`，不是
     * `Exception`），不在这里拦掉就会直接崩 —— 那比「播不了」难查得多。
     */
    class UnsupportedAbi(message: String) : MagnetUnavailable(message)
}

/**
 * 一条磁力的播放会话。
 *
 * 生命周期：`prepare()`（挂给 ExoPlayer 前调）→ 播放期反复问 `availableBytesAt()` →
 * `close()`（离开播放页调）。
 */
class MagnetStream(
    private val session: TorrentSession,
    private val manager: SessionManager,
    private val dir: File,
    private val magnet: String
) {

    /** 等种子信息的上限。超时即判「这条资源现在拿不到内容」。 */
    private val metadataTimeoutMs = 30_000L

    /**
     * 起播缓冲：连续前缀至少到这么多字节才让 ExoPlayer 开始读。
     * 太小会让引擎刚连上对端就被判卡死，太大首帧时间难看 —— 8MB 大约是 mp4 头 + moov 的量级。
     */
    private val startupBufferBytes = 8L * 1024 * 1024

    private val headWindowBytes = 32L * 1024 * 1024
    private val tailWindowBytes = 16L * 1024 * 1024

    private var handle: TorrentHandle? = null
    private var storage: FileStorage? = null
    private var mainFileIndex = -1
    private var mainFileOffset = 0L
    private var mainFileBytes = 0L
    private var pieceBytes = 1L
    private var firstPiece = 0
    private var lastPiece = 0

    /** 连续已核对前缀的第一个未核对分片号，用于「起播就绪」与进度显示。 */
    private var contiguousPiece = 0

    /** 见过的最远的可读前沿分片号；只有它继续往后推才算有进展。 */
    private var frontierPiece = -1

    @Volatile
    private var prepared = false

    /** 上一次推进前沿的时刻，用于停滞判定。 */
    private var lastAdvanceAt = 0L

    /**
     * 挂进会话并等种子信息。
     *
     * @throws MagnetUnavailable 播不了的明确原因，UI 直接拿去显示。
     */
    @Throws(MagnetUnavailable::class)
    fun prepare() {
        if (prepared) return
        val ec = error_code()
        val params: add_torrent_params = runCatching { libtorrent.parse_magnet_uri(magnet, ec) }
            .getOrNull() ?: throw MagnetUnavailable.BadMagnet("磁力链接格式不对")
        if (ec.failed()) throw MagnetUnavailable.BadMagnet("磁力链接格式不对")

        params.setTrackers(session.trackersFor(magnet))
        params.setSave_path(dir.absolutePath)

        val addEc = error_code()
        val added = runCatching { TorrentHandle(manager.swig().add_torrent(params, addEc)) }
            .getOrNull()
        if (added == null || addEc.failed()) {
            throw MagnetUnavailable.BadMagnet("任务启动失败：${addEc.message()}")
        }
        handle = added
        added.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)

        val deadline = SystemClock.elapsedRealtime() + metadataTimeoutMs
        var info: TorrentInfo? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            info = runCatching { added.torrentFile() }.getOrNull()
            if (info != null) break
            Thread.sleep(300)
        }
        if (info == null) {
            // 摘掉再抛：否则这条会在后台一直抢带宽，下一条更连不上。
            runCatching { manager.remove(added) }
            handle = null
            session.refreshNetworkState()
            val wall = if (session.networkState.firewalled) "，本机 BT 端口不可入站" else ""
            throw MagnetUnavailable.NoMetadata(
                "暂时连不做种者（${metadataTimeoutMs / 1000} 秒没拿到种子信息$wall）"
            )
        }
        try {
            selectMainFile(added, info)
        } catch (e: MagnetUnavailable) {
            // 选不到主文件也得摘任务：留在会话里就是一条纯抢带宽的僵尸任务。
            runCatching { manager.remove(added) }
            handle = null
            throw e
        }
        prepared = true
        session.log("种子信息已到 ${infoHashShort()} 主文件=${mainFileName()}")
    }

    /** 只下最大的视频文件，其余置 IGNORE —— 在线播放不需要整包。 */
    private fun selectMainFile(h: TorrentHandle, info: TorrentInfo) {
        val files = info.files()
        var best = -1
        var bestBytes = -1L
        for (i in 0 until files.numFiles()) {
            val path = files.filePath(i).lowercase(Locale.US)
            val bytes = files.fileSize(i)
            if (VIDEO_EXT.any { path.endsWith(it) } && bytes > bestBytes) {
                best = i
                bestBytes = bytes
            }
        }
        if (best < 0 || bestBytes <= 0) {
            throw MagnetUnavailable.NoVideoFile("这个种子里没有可播放的视频文件")
        }
        storage = files
        mainFileIndex = best
        mainFileBytes = bestBytes
        mainFileOffset = files.fileOffset(best)
        pieceBytes = files.pieceLength().coerceAtLeast(1).toLong()
        firstPiece = (mainFileOffset / pieceBytes).toInt()
        lastPiece = ((mainFileOffset + mainFileBytes - 1) / pieceBytes).toInt()
        contiguousPiece = firstPiece
        frontierPiece = firstPiece - 1
        lastAdvanceAt = SystemClock.elapsedRealtime()

        val priorities = Array(files.numFiles()) { if (it == best) Priority.DEFAULT else Priority.IGNORE }
        runCatching { h.prioritizeFiles(priorities) }
            .onFailure { session.log("文件优先级设置失败 ${it.message}") }

        applyWindows(h)
    }

    /**
     * 头尾双窗口。
     *
     * 头窗口保证「立刻能开播」；尾窗口保证「容器索引读得到」——非 faststart 的 mp4 把 moov
     * 放在文件末尾，纯顺序下载要等到最后一片才拿到，用户看到的就是一直转圈。
     */
    private fun applyWindows(h: TorrentHandle) {
        val headEnd = min(lastPiece, firstPiece + (headWindowBytes / pieceBytes).toInt())
        val tailStart = max(firstPiece, lastPiece - (tailWindowBytes / pieceBytes).toInt())
        runCatching {
            for (p in firstPiece..headEnd) h.piecePriority(p, Priority.TOP_PRIORITY)
            for (p in tailStart..lastPiece) h.piecePriority(p, Priority.TOP_PRIORITY)
        }.onFailure { session.log("窗口优先级设置失败 ${it.message}") }
        session.log("分片窗口 头=$firstPiece..$headEnd 尾=$tailStart..$lastPiece 片长=${pieceBytes / 1024}KB")
    }

    /**
     * 从主文件的 [position] 起连续可读的字节数（上限 [wanted]）。
     *
     * 只认「已核对的分片」：libtorrent 校验过 hash 才写盘，没核对的区间在文件里是空洞。
     * 核对过但还在 libtorrent 磁盘缓存里的分片要调用方先 [flushPendingWrites] 才真在文件里，
     * 所以这里不 flush（每读一次就 flush 太浪费），由 [MagnetDataSource] 在等货时统一催一次。
     */
    fun availableBytesAt(position: Long, wanted: Int): Long {
        val h = handle ?: return 0L
        if (mainFileBytes <= 0) return 0L
        val fileEnd = mainFileOffset + mainFileBytes
        val globalStart = mainFileOffset + position
        if (globalStart >= fileEnd) return 0L
        var piece = (globalStart / pieceBytes).toInt()
        var available = 0L
        while (piece <= lastPiece && available < wanted) {
            if (!runCatching { h.havePiece(piece) }.getOrDefault(false)) break
            // 换算走纯函数（见 MagnetStreamPolicy），**别内联回来** —— 内联了就没法单测，
            // 而这里的三重钳制（片首/片尾/文件尾）正是最容易算错的地方。
            available += pieceContribution(globalStart + available, piece, pieceBytes, fileEnd)
            piece++
        }
        noteFrontier(piece)
        return min(available, wanted.toLong())
    }

    /** 催 libtorrent 把已核对的分片真正写进文件。 */
    fun flushPendingWrites() {
        runCatching { handle?.flushCache() }
    }

    /**
     * 记录可读前沿。只有它真的往后挪过，才重置停滞计时 —— 判「这条播不了」全靠这个，
     * 所以起播后（不再调 [contiguousBytes]）也必须由 [availableBytesAt] 喂进来。
     */
    private fun noteFrontier(piece: Int) {
        if (piece > frontierPiece) {
            frontierPiece = piece
            lastAdvanceAt = SystemClock.elapsedRealtime()
        }
    }

    /** 连续前缀字节数，用于起播就绪判定与进度显示。 */
    fun contiguousBytes(): Long {
        val h = handle ?: return 0L
        var piece = contiguousPiece
        val limit = lastPiece + 1
        while (piece < limit && runCatching { h.havePiece(piece) }.getOrDefault(false)) piece++
        if (piece != contiguousPiece) {
            contiguousPiece = piece
        }
        noteFrontier(piece)
        val globalFrontier = min(contiguousPiece.toLong() * pieceBytes, mainFileOffset + mainFileBytes)
        return (globalFrontier - mainFileOffset).coerceAtLeast(0L)
    }

    /** 起播判定：连续前缀是否已经够首帧。 */
    fun readyToStart(): Boolean =
        prepared && contiguousBytes() >= min(startupBufferBytes, mainFileBytes)

    /** 一段时间没推进就认为播不了（假种/吸血源的典型表现）。 */
    fun stalled(): Boolean = prepared && SystemClock.elapsedRealtime() - lastAdvanceAt > STALL_TIMEOUT_MS

    /**
     * 播放位置移动后把前面若干分片设上 deadline，让引擎按播放进度优先取货。
     * 由 DataSource 在 seek/read 时调用。
     */
    fun followPlayback(position: Long) {
        val h = handle ?: return
        if (!prepared) return
        val from = ((mainFileOffset + position) / pieceBytes).toInt()
        val to = min(lastPiece, from + AHEAD_PIECES)
        runCatching {
            h.clearPieceDeadlines()
            for (p in from..to) h.setPieceDeadline(p, DEADLINE_MS)
        }
    }

    /** 引擎侧的真实连接情况，用于「播不了」时给用户解释原因。 */
    fun peers(): Int = runCatching { handle?.status()?.numPeers() ?: 0 }.getOrDefault(0)

    fun seeds(): Int = runCatching { handle?.status()?.numSeeds() ?: 0 }.getOrDefault(0)

    /**
     * 引擎实测的下载速率（字节/秒）。播放页缓冲时显示的就是这个数。
     *
     * ⚠️ 别拿「播放器读了多少字节」代替：磁力读的是本地落盘文件，卡住的时候文件里的
     * 存量照样能读，那个数字会一直很好看 —— 而用户要看的正是「网络还在不在出货」。
     * libtorrent 内部已经平滑过，不用再套一层采样窗口。
     */
    fun downloadBytesPerSecond(): Long =
        runCatching { handle?.status()?.downloadRate() ?: 0 }.getOrDefault(0).toLong()

    /**
     * 一行引擎侧实况，用来把「播不了」分成三类完全不同的病：
     * 连不上对端（peers 一直 0）、有对端但取不到货（peers>0 而 contiguous 不动）、
     * 货到了但没落盘（contiguous 涨、onDisk 不涨）。
     */
    fun statusLine(): String {
        val h = handle
        val st = runCatching { h?.status() }.getOrNull()
        val peers = runCatching { st?.numPeers() ?: 0 }.getOrDefault(0)
        val seeds = runCatching { st?.numSeeds() ?: 0 }.getOrDefault(0)
        val rate = runCatching { st?.downloadRate() ?: 0 }.getOrDefault(0)
        // 全网有几份完整副本：peers>0 但这个数是 0，说明连上的都是些「没有完整货」的对端。
        val copies = runCatching { st?.distributedCopies() ?: 0f }.getOrDefault(0f)
        val state = runCatching { st?.state()?.toString() }.getOrNull() ?: "?"
        val onDisk = backingFile()?.length() ?: -1L
        return "peers=$peers seeds=$seeds 速率=${rate / 1024}KB/s 副本=${"%.1f".format(copies)} " +
            "状态=$state 连续=${contiguousBytes() / 1024}KB 落盘=${onDisk / 1024}KB " +
            "总=${mainFileBytes / 1024}KB 元数据=$prepared 文件=${mainFileName()}"
    }

    fun totalBytes(): Long = mainFileBytes

    fun mainFileName(): String = storage?.filePath(mainFileIndex) ?: ""

    /** 主视频在磁盘上的实际路径（save_path + 种子内路径）。 */
    fun backingFile(): File? {
        val files = storage ?: return null
        if (mainFileIndex < 0) return null
        return File(dir, files.filePath(mainFileIndex))
    }

    fun infoHashShort(): String = magnet.substringAfter("btih").take(12)

    /** 摘任务 + 删落盘文件。必须成对调用，否则下一条磁力会跟上一条抢带宽。 */
    fun close(deleteFiles: Boolean = true) {
        prepared = false
        handle?.let { h -> runCatching { manager.remove(h) } }
        handle = null
        storage = null
        if (deleteFiles) runCatching { dir.deleteRecursively() }
    }

    companion object {
        private val VIDEO_EXT = listOf(
            ".mp4", ".mkv", ".ts", ".m2ts", ".avi", ".wmv", ".mov", ".flv", ".m4v", ".webm", ".rmvb"
        )
        private const val DEADLINE_MS = 15_000
        private const val AHEAD_PIECES = 8

        /** 前沿多久不推进就算「在线播不了」。 */
        const val STALL_TIMEOUT_MS = 25_000L
    }
}
