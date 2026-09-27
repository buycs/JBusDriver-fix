package me.jbusdriver.torrent

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import kotlin.math.min

/** 播不了时抛给 ExoPlayer 的异常；原因对象同时留在 [MagnetPlayback.failure] 里给 UI 用。 */
@OptIn(UnstableApi::class)
class MagnetPlaybackException(val reason: MagnetUnavailable) : IOException(reason.message)

/**
 * 磁力在线播放的 Media3 `DataSource`。
 *
 * ## 它读的是「正在增长的本地下落盘文件」
 *
 * BT 引擎（[TorrentSession]）把主视频分片边下边写进 `cacheDir/magnet_media/<hash>/`。
 * 本类按 ExoPlayer 要的偏移把这些字节喂出去，并且：
 *
 * - **按分片粒度钳制可读范围**：文件一创建就是全长，没下到的位置在文件里是空洞，
 *   直接读会把 0 字节当音视频数据喂给解析器（表现为花屏/一直转圈）。
 * - **`open()` 返回真实剩余长度**：ExoPlayer 靠它推出文件总长，才能反向 seek 到文件末尾
 *   读非 faststart mp4 的 `moov`；返回 `LENGTH_UNSET` 等于放弃这类文件。
 * - **缺数据时阻塞等货**：和网卡读 socket 是一回事，加载线程停在这儿，播放器保持 BUFFERING；
 *   引擎侧由双窗口 + deadline 调度在推进（见 [MagnetStream]）。
 * - **等不到就抛**：前沿超过 [MagnetStream.STALL_TIMEOUT_MS] 不推进就抛 [MagnetUnavailable.Stalled]，
 *   播放页据此提示「这条资源在线播不了」。
 */
@OptIn(UnstableApi::class)
class MagnetDataSource(private val owner: MagnetPlayback) : DataSource {

    private var stream: MagnetStream? = null
    private var file: RandomAccessFile? = null
    private var uri: Uri = Uri.parse(owner.magnet)
    private var position = 0L
    private var bytesRemaining = C.LENGTH_UNSET.toLong()

    override fun addTransferListener(transferListener: TransferListener) = Unit

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val s = try {
            owner.awaitStream()
        } catch (e: MagnetUnavailable) {
            throw fail(e)
        }
        val backing = s.backingFile() ?: throw fail(MagnetUnavailable.Stalled("本地缓存文件已丢失"))
        val raf = RandomAccessFile(backing, "r")
        stream = s
        file = raf
        uri = dataSpec.uri
        position = dataSpec.position
        bytesRemaining = dataSpec.length
        raf.seek(position)
        s.followPlayback(position)
        val total = s.totalBytes()
        // 返回「从 position 起还有多少字节」，调用方会加上 position 还原成文件总长。
        return if (total <= 0) C.LENGTH_UNSET.toLong() else (total - position).coerceAtLeast(0L)
    }

    override fun getUri(): Uri = uri

    @Throws(IOException::class)
    override fun close() {
        runCatching { file?.close() }
        file = null
        stream = null
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val raf = file ?: throw fail(MagnetUnavailable.Stalled("播放已停止"))
        val s = stream ?: throw fail(MagnetUnavailable.Stalled("播放已停止"))
        if (length == 0) return 0
        if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining <= 0) return C.RESULT_END_OF_INPUT
        val total = s.totalBytes()
        if (total > 0 && position >= total) return C.RESULT_END_OF_INPUT

        val wanted = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            min(length.toLong(), bytesRemaining).toInt()
        }
        val readable = waitForBytes(s, raf, wanted)
        raf.seek(position)
        val n = raf.read(buffer, offset, readable)
        if (n < 0) return C.RESULT_END_OF_INPUT
        position += n
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        return n
    }

    /**
     * 等到 [position] 起至少 1 字节、至多 [wanted] 字节**真的能从文件读出**。
     *
     * 两道钳制缺一不可：分片得核对过（[MagnetStream.availableBytesAt]），还得已经落进文件
     * （`raf.length()`）—— libtorrent 核对完可能还在自己的磁盘缓存里。每轮催一次写盘，
     * 前沿长时间不动就判定播不了。
     */
    @Throws(IOException::class)
    private fun waitForBytes(s: MagnetStream, raf: RandomAccessFile, wanted: Int): Int {
        var lastPoke = 0L
        while (true) {
            if (owner.released) throw InterruptedIOException("播放已取消")
            // ExoPlayer 对 IOException 会重试好几轮。拿不到种子信息这类原因重试也不会变，
            // 再等一轮等于把「播不了」的提示推迟几十秒，所以这里直接快速失败。
            owner.terminalFailure?.let { throw MagnetPlaybackException(it) }
            val onDisk = (raf.length() - position).coerceAtLeast(0L)
            val available = min(s.availableBytesAt(position, wanted), onDisk)
            if (available > 0) {
                // 真的读出数据了：之前若记过停滞，说明已经自愈。
                owner.clearRecoverableFailure()
                return available.coerceAtMost(wanted.toLong()).toInt()
            }
            if (s.stalled()) {
                throw fail(MagnetUnavailable.Stalled(
                    "下载停滞，在线播不了（做种者 ${s.seeds()}、连接 ${s.peers()}）"
                ))
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastPoke >= POKE_INTERVAL_MS) {
                lastPoke = now
                s.flushPendingWrites()
                s.followPlayback(position)
            }
            sleepQuietly(POLL_INTERVAL_MS)
        }
    }

    private fun fail(reason: MagnetUnavailable): MagnetPlaybackException {
        owner.recordFailure(reason)
        return MagnetPlaybackException(reason)
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60L
        private const val POKE_INTERVAL_MS = 500L
    }
}

/**
 * 一条磁力的播放后端：持有 BT 任务，并充当 ExoPlayer 的 [DataSource.Factory]。
 *
 * ExoPlayer 一次播放会反复 create/open/close DataSource（嗅探、起播、seek 各来一轮），
 * 所以 BT 任务**不能**跟着 DataSource 建了又拆 —— 它是「一次播放一个」，由本对象保管，
 * 退出播放页或重新 prepare 时调 [release]。
 */
@OptIn(UnstableApi::class)
class MagnetPlayback(
    context: Context,
    val magnet: String
) : DataSource.Factory {

    private val appContext = context.applicationContext
    private val lock = Any()

    /** 播不了的原因，播放页拿去出准确文案。 */
    @Volatile
    var failure: MagnetUnavailable? = null
        private set

    /**
     * 重试也不会变好的原因：种子信息都没拿到，或者种子里没视频。
     * [Stalled] 不在其中 —— 那种是带宽抖动，再给一次机会可能就来货了。
     */
    val terminalFailure: MagnetUnavailable? get() = failure?.takeIf { it !is MagnetUnavailable.Stalled }

    @Volatile
    var released = false
        private set

    @Volatile
    private var stream: MagnetStream? = null

    /** 冒烟/诊断用：当前挂着 BT 任务的话把它透出去（不创建、不抢生命周期）。 */
    internal val currentStream: MagnetStream? get() = stream

    /** 建任务 → 等种子信息 → 等首帧缓冲，阻塞到能开播或明确失败。 */
    @Throws(IOException::class)
    internal fun awaitStream(): MagnetStream {
        terminalFailure?.let { throw MagnetPlaybackException(it) }
        val s = obtainStream()
        try {
            s.prepare()
        } catch (e: MagnetUnavailable) {
            recordFailure(e)
            throw e
        }
        while (!s.readyToStart()) {
            if (released) throw InterruptedIOException("播放已取消")
            if (s.stalled()) {
                val hint = if (s.peers() == 0) "（连不上做种者）" else "（有做种者但取不到数据）"
                throw fail(MagnetUnavailable.Stalled("这条资源暂时在线播不了$hint"))
            }
            sleepQuietly(STARTUP_POLL_INTERVAL_MS)
        }
        s.flushPendingWrites()
        // 起播成功 = 之前的停滞已自愈，把那条可恢复的失败记录清掉。
        clearRecoverableFailure()
        return s
    }

    override fun createDataSource(): DataSource = MagnetDataSource(this)

    internal fun recordFailure(reason: MagnetUnavailable) {
        // 只记第一个原因：后面的「停滞」多半是同一个病根的并发症，最早的诊断最有用。
        if (failure == null) failure = reason
    }

    /**
     * 有了实际进展（读出数据 / 起播成功）之后，清掉**可恢复**的失败记录。
     *
     * 判据在 [shouldClearFailureOnProgress]：只清 `Stalled`（带宽抖动，会自愈），
     * 终态原因保留。不清的话，「播成功」的场景里 `magnetFailure` 仍带着失败原因，
     * 诊断时会被误导 —— 那属于「把成功说成失败」。
     */
    internal fun clearRecoverableFailure() {
        if (shouldClearFailureOnProgress(failure)) failure = null
    }

    /** 摘掉 BT 任务并删掉落盘缓存；可重复调用。 */
    fun release() {
        val s: MagnetStream?
        synchronized(lock) {
            released = true
            s = stream
            stream = null
        }
        s?.close()
    }

    private fun obtainStream(): MagnetStream = synchronized(lock) {
        if (released) throw InterruptedIOException("播放已取消")
        stream ?: try {
            TorrentSession.get(appContext)
                .open(magnet, magnet.substringAfter("btih", "task").take(20))
                .also { stream = it }
        } catch (e: LinkageError) {
            // 走到这里基本只有一个原因：该 ABI 没有 libtorrent4j.so（上游不发 32 位 x86）。
            // 必须把 Error 转成正常的失败原因，否则「播不了」会变成「应用崩了」。
            throw fail(
                MagnetUnavailable.UnsupportedAbi(
                    "当前设备架构（${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}）不支持磁力在线播放"
                )
            )
        }
    }

    private fun fail(reason: MagnetUnavailable): MagnetPlaybackException {
        recordFailure(reason)
        return MagnetPlaybackException(reason)
    }

    private fun sleepQuietly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val STARTUP_POLL_INTERVAL_MS = 250L
    }
}
