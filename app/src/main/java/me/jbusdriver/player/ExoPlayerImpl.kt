package me.jbusdriver.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import me.jbusdriver.torrent.MagnetPlayback
import me.jbusdriver.torrent.MagnetUnavailable
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

// ⚠️ 注意这里用的是 androidx.annotation.OptIn，**不是** kotlin.OptIn。
// Media3 的 UnstableApi 是 AndroidX 的 lint 注解（没有 @RequiresOptIn 元注解），
// kotlin.OptIn 对它无效 —— 只会换来一条 "has no effect" 警告。
// 本类用到的 DefaultHttpDataSource.Factory 的 setter、HlsMediaSource.Factory、
// DefaultMediaSourceFactory.setDataSourceFactory、Player.setMediaSource 都属于 @UnstableApi，
// 必须这样 opt-in 才能过 Lint。
@androidx.annotation.OptIn(UnstableApi::class)
class ExoPlayerImpl(context: Context) {

    /**
     * 从网络上真正读到的累计字节。
     *
     * ⚠️ 磁力**不**记这里：磁力读的是引擎边下边写的本地文件，把这些字节当「网速」报出去
     * 等于自欺欺人（尤其卡住的时候 —— 文件里的存量还能读，网络早就断了）。
     * 磁力那条走 libtorrent 的实测速率，见 [pollRequestSpeed]。
     *
     * 写发生在 ExoPlayer 的加载线程，读在播放页的轮询（主线程），所以必须是原子计数。
     */
    private val httpBytesRead = AtomicLong()

    /** 网速采样的滚动窗口。只在 [pollRequestSpeed]（主线程轮询）里读写，不需要加锁。 */
    private val speedSamples = ArrayList<SpeedSample>(SPEED_SAMPLE_LIMIT)

    val player: ExoPlayer = createPlayer(context)

    /** 当前磁力播放后端；非磁力为 null。BT 任务的生命周期跟着它，不跟 DataSource。 */
    private var magnetPlayback: MagnetPlayback? = null

    /**
     * 磁力播不了的原因（`NoMetadata`/`Stalled`/…），供播放页出准确提示。
     * 只在 [prepare] 之后、且 URL 是磁力时有意义。
     */
    val magnetFailure: MagnetUnavailable? get() = magnetPlayback?.failure

    /** 引擎侧实况一行（对端数/落盘/连续前缀）；非磁力或未建任务时为 null。 */
    val magnetStatus: String? get() = magnetPlayback?.currentStream?.statusLine()

    private fun createPlayer(context: Context): ExoPlayer {
        // 只把「HTTP 数据源」换成带计数的转发层，媒体源判定仍然交给
        // DefaultMediaSourceFactory —— file:///content://、DASH 的自动识别都在它里面。
        // 这里给的是裸的 DefaultHttpDataSource.Factory，和改造前 Builder 内置的那份等价。
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(counting(DefaultHttpDataSource.Factory()))
            )
            .build()
    }

    /** 在任意数据源外面套一层字节计数。 */
    private fun counting(delegate: DataSource.Factory): DataSource.Factory =
        ByteCountingDataSourceFactory(delegate) { bytes -> httpBytesRead.addAndGet(bytes) }

    fun prepare(context: Context, url: String, headers: Map<String, String> = emptyMap()) {
        // 一次只允许一条磁力在跑：上一条必须先从 BT 会话里摘掉，否则两条抢带宽，
        // 而且会话的 active_limit=1 会把新任务直接排队饿死。
        releaseMagnet()

        val userAgent = "JavCinema/${android.os.Build.VERSION.SDK_INT}"

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        if (headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(headers)
        }

        val mediaItem = MediaItem.fromUri(url)

        when {
            url.startsWith("magnet:") -> {
                val playback = MagnetPlayback(context, url)
                magnetPlayback = playback
                val magnetSource = ProgressiveMediaSource.Factory(playback)
                    .createMediaSource(mediaItem)
                player.setMediaSource(magnetSource)
            }
            url.contains(".m3u8") -> {
                val hlsMediaSource = HlsMediaSource.Factory(counting(httpDataSourceFactory))
                    .createMediaSource(mediaItem)
                player.setMediaSource(hlsMediaSource)
            }
            else -> {
                // 走 ExoPlayer 自带的默认 DataSource（内部会自己拼 http scheme），
                // 和改造前的 `setMediaItem` 完全一致 —— 这里不要换成 ProgressiveMediaSource，
                // 否则会绕过 DefaultMediaSourceFactory 对 file:///content:// 和 DASH 的自动判定。
                player.setMediaItem(mediaItem)
            }
        }
        player.prepare()
        player.playWhenReady = true
    }

    fun play() {
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun togglePlay() {
        player.playWhenReady = !player.playWhenReady
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player.currentPosition

    fun getDuration(): Long = player.duration

    /**
     * 已经缓冲到的位置（毫秒）。
     *
     * 用在进度条上画「已缓存」那一段。HLS 是分片拉取的，这个值会**跳着**涨
     * （一整个分片下完才前进），不是平滑爬升 —— 所以别拿它做「网速」之类的判断。
     */
    fun getBufferedPosition(): Long = player.bufferedPosition

    /**
     * 实时请求网速（字节/秒），给播放页缓冲时的角标用。
     *
     * - **磁力**：取 libtorrent 的实测下载速率。引擎自己已经平滑过，不用再开窗；
     *   而且磁力唯一有意义的网络数字就是它 —— 播放器那边读的是本地文件。
     * - **HTTP / HLS**：每次调用打一个「累计字节」采样点，用滚动窗口首尾相减。
     *
     * ⚠️ 采样点是在这里产生的，所以**必须由 UI 定时调**（播放页是 200ms 一次）；
     * 不调就没有第二个点，速率永远是 0。
     */
    fun pollRequestSpeed(): Long {
        if (magnetPlayback != null) {
            // 种子信息都还没拿到的时候 currentStream 是 null，这时候确实一点货都没有，
            // 报 0 比「沿用 HTTP 那套（全程 0）」更诚实，也更好读。
            return magnetPlayback?.currentStream?.downloadBytesPerSecond() ?: 0L
        }
        if (speedSamples.size >= SPEED_SAMPLE_LIMIT) speedSamples.removeAt(0)
        speedSamples.add(SpeedSample(SystemClock.elapsedRealtime(), httpBytesRead.get()))
        return requestSpeedBytesPerSecond(speedSamples, SPEED_WINDOW_MS)
    }

    fun isPlaying(): Boolean = player.isPlaying

    /**
     * 设置播放倍速。传 1.0f 即恢复正常速度。
     *
     * ⚠️ 这是**有状态**的：ExoPlayer 会一直保持这个倍速，直到再次设置。
     * 长按快放那种「临时加速」必须在松手时显式设回用户选的倍速，
     * 不能指望它自己恢复。
     */
    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
    }

    fun setVolume(volume: Float) {
        player.volume = volume
    }

    fun release() {
        player.release()
        releaseMagnet()
    }

    /**
     * 摘掉 BT 任务并删掉落盘缓存。
     *
     * ⚠️ 必须在 `player.release()` 之后调：磁力起播时加载线程可能正阻塞在等分片，
     * [MagnetPlayback.release] 先置 `released` 让等待循环立刻退出。
     */
    private fun releaseMagnet() {
        magnetPlayback?.release()
        magnetPlayback = null
    }

    companion object {
        /**
         * 网速的采样窗口。
         *
         * 太短会抖（HLS 要一整个分片下完字节才涨），太长把突发抹平 —— 已经卡住了还在
         * 显示刚才的高速。1.5 秒配播放页 200ms 的轮询，窗口里大约 7 个点。
         */
        private const val SPEED_WINDOW_MS = 1_500L

        /** 采样点上限：窗口早就丢掉了，留着只会一直涨。 */
        private const val SPEED_SAMPLE_LIMIT = 10
    }
}

/**
 * 一层纯转发，只多做一件事：把真正读到的字节累计给 [sink]，给播放页的实时网速用。
 *
 * 为什么不挂在 Media3 的 analytics 上：1.5.1 的 `AnalyticsListener`（javap 核对过）里
 * **已经没有** `onLoadProgress`，而直链 mp4 的持续下载走的是
 * `ProgressiveMediaPeriod.ExtractingLoadable`，`Loader` 也不上报字节数。
 * 唯一稳定的观测点就是 `DataSource.read()` 本身。
 */
// ⚠️ 顶层类**不会**继承 [ExoPlayerImpl] 上的 `@androidx.annotation.OptIn` ——
// 那个注解只作用于被标注的类自身。这两个类单独写在这里，必须各自再标一次，
// 否则会报 16 条 `UnsafeOptInUsageError`（`DataSource` / `DataSpec` / `TransferListener`
// 都带 @UnstableApi）。别把注解挪到文件级或指望继承。
@androidx.annotation.OptIn(UnstableApi::class)
private class ByteCountingDataSource(
    private val delegate: DataSource,
    private val sink: (Long) -> Unit
) : DataSource {

    // 转发监听器：ExoPlayer 的流量统计是靠 `addTransferListener` 挂到最里层数据源上的，
    // 包一层不转发的话，带宽估计会被静默喂瞎。
    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long = delegate.open(dataSpec)

    override fun getUri(): Uri? = delegate.uri

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = delegate.read(buffer, offset, length)
        if (read > 0) sink(read.toLong())
        return read
    }

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        delegate.close()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
private class ByteCountingDataSourceFactory(
    private val delegate: DataSource.Factory,
    private val sink: (Long) -> Unit
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ByteCountingDataSource(delegate.createDataSource(), sink)
}
