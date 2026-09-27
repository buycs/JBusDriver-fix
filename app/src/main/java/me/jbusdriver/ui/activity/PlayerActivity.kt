package me.jbusdriver.ui.activity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import me.jbusdriver.R
import me.jbusdriver.player.ExoPlayerImpl

/**
 * 播放页：磁力流与在线流共用同一个页面，靠 url 前缀分流，判定在
 * [ExoPlayerImpl.prepare] 里：
 * - `magnet:` → 进程内 BT 引擎边下边播（见 me.jbusdriver.torrent）
 * - 含 `.m3u8` → HLS
 * - 其余 → ExoPlayer 默认（直链 mp4 / DASH / file://）
 *
 * 这一页只管「把画面放出来 + 播不了时说清原因」，不做手势、倍速、选集 ——
 * JavCinema 那套 50K 的 Compose 播放页没有对应搬过来。
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayerImpl? = null

    private val mediaUrl by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val mediaTitle by lazy { intent.getStringExtra(EXTRA_TITLE).orEmpty() }
    private val referer by lazy { intent.getStringExtra(EXTRA_REFERER).orEmpty() }
    private val isMagnet by lazy { mediaUrl.startsWith(MAGNET_PREFIX) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (mediaUrl.isBlank()) {
            finish()
            return
        }
        // 播放期间别让屏幕自己灭掉
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        enterFullScreen()

        val playerView = findViewById<PlayerView>(R.id.pv_player)
        val hintContainer = findViewById<View>(R.id.ll_player_hint)
        val hintText = findViewById<TextView>(R.id.tv_player_hint)
        val progress = findViewById<ProgressBar>(R.id.pb_player)

        fun showHint(text: String, spinning: Boolean) {
            hintContainer.visibility = View.VISIBLE
            hintText.text = text
            progress.visibility = if (spinning) View.VISIBLE else View.GONE
            // 提示层和 PlayerView 的中央按钮都锚在正中间，控制器不藏就会叠成一团
            // （实测：金色转圈压在白底暂停键上）。
            playerView.useController = false
        }

        fun hideHint() {
            hintContainer.visibility = View.GONE
            playerView.useController = true
        }

        val engine = ExoPlayerImpl(this)
        exoPlayer = engine
        playerView.player = engine.player

        engine.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> showHint(bufferingHint(), true)
                    Player.STATE_READY, Player.STATE_ENDED -> hideHint()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showHint(failureHint(error), false)
            }
        })

        showHint(bufferingHint(), true)
        engine.prepare(this, mediaUrl, streamHeaders())
    }

    /**
     * 站点直链通常校验来源：不带 Referer / 会话 Cookie 会被 CDN 直接拒。
     * Cookie 从全局 CookieManager 取 —— 在线播放前 WebView 已经访问过该站，会话就在里面。
     */
    private fun streamHeaders(): Map<String, String> = buildMap {
        if (referer.isNotBlank()) put("Referer", referer)
        CookieManager.getInstance().getCookie(mediaUrl)
            ?.takeIf { it.isNotBlank() }
            ?.let { put("Cookie", it) }
    }

    /** 磁力起播要等种子信息（上限 30s）和第一个分片，文案得说清在等什么。 */
    private fun bufferingHint(): String {
        val what = if (isMagnet) "正在连接做种者，拿到种子信息后自动开播…" else "正在缓冲…"
        return if (mediaTitle.isBlank()) what else "$mediaTitle\n$what"
    }

    /**
     * 播不了的原因。
     *
     * 磁力侧优先用引擎给的具体原因（`NoMetadata`/`NoVideoFile`/`Stalled`…）——
     * 「暂时连不做种者」和「种子里没有视频文件」对用户是两件事，不能混成一句「播放失败」。
     */
    private fun failureHint(error: PlaybackException): String {
        val reason = if (isMagnet) {
            exoPlayer?.magnetFailure?.message ?: "磁力在线播放失败"
        } else {
            "播放失败，请检查网络后重试"
        }
        return reason
    }

    private fun enterFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * 切后台 / 被完全遮住时暂停。
     *
     * 用 `onStop` 而不是 `onPause`：`onPause` 在弹窗、多窗口失焦时也会触发，太激进。
     * 回来**不自动续播**，让用户自己点 —— 否则一解锁就莫名其妙出声。
     */
    override fun onStop() {
        super.onStop()
        exoPlayer?.player?.pause()
    }

    override fun onDestroy() {
        // release 里会先摘掉 BT 任务再删落盘缓存，顺序不能反（见 ExoPlayerImpl.release）
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "extra_player_url"
        private const val EXTRA_TITLE = "extra_player_title"
        private const val EXTRA_REFERER = "extra_player_referer"
        private const val MAGNET_PREFIX = "magnet:"

        /**
         * 起播放页。url 支持 `magnet:` / `.m3u8` / 直链。
         * [title] 只用于缓冲提示的第一行，可为空。
         * [referer] 给在线直链用（站点 CDN 会校验来源），磁力不需要。
         */
        fun start(context: Context, url: String, title: String? = null, referer: String? = null) {
            if (url.isBlank()) return
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_REFERER, referer)
            }
            // 非 Activity 上下文起 Activity 必须带 NEW_TASK，否则 AndroidRuntimeException。
            // 现有调用方都传 Activity，这里是兜底（磁力弹窗走 PlayerLauncher 传的是 viewContext，
            // 未来若从 Application / Service 调进来就会踩）。
            if (!context.isActivityContext()) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

/**
 * `context` 是不是（或包装着）Activity。
 *
 * 不能直接 `context is Activity`：Fragment 的 `viewContext` 常常是
 * `ContextThemeWrapper`，得沿着 [ContextWrapper.baseContext] 一层层剥。
 */
private fun Context.isActivityContext(): Boolean {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return true
        current = current.baseContext
    }
    return false
}
