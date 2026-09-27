package me.jbusdriver.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import me.jbusdriver.R
import me.jbusdriver.ui.missav.MISSAV_NOT_FOUND_MESSAGE
import me.jbusdriver.ui.missav.MISSAV_STREAM_PROBE_JS
import me.jbusdriver.ui.missav.MissavResolveAction
import me.jbusdriver.ui.missav.decideSearchOutcome
import me.jbusdriver.ui.missav.extractMissavStreamUrl
import me.jbusdriver.ui.missav.isAcceptableStreamUrl
import me.jbusdriver.ui.missav.isAllowedMissavNavigation
import me.jbusdriver.ui.missav.isMissavChallengeTitle
import me.jbusdriver.ui.missav.isMissavChallengeUrl
import me.jbusdriver.ui.missav.isMissavPlayUrl
import me.jbusdriver.ui.missav.isMissavSearchUrl
import me.jbusdriver.ui.missav.missavSearchUrl
import me.jbusdriver.ui.missav.unescapeJsString
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 在线播放：用番号搜 MissAV，页面在 WebView 里跑，嗅到 HLS/MP4 直链就交给
 * [PlayerActivity]（Media3）全屏播。
 *
 * 取流为什么必须在网络层旁听（`shouldInterceptRequest`）：
 * - 播放器走 MSE，`<video>` 的 `currentSrc` 是 `blob:https://…`，对 Media3 没有意义；
 * - 站点把真正的 m3u8 打包混淆在 `eval(function(p,a,c,k,e,d){…})` 里，整页 HTML 里
 *   没有明文 `.m3u8`；
 * - 所以唯一稳的线索是**播放器实际发出的那次请求**。
 *
 * 与 JavCinema 的 Compose 版相比，这里砍掉了「遮罩盖住站点页面」那套 —— 站点页面
 * 全程可见，撞上人机验证时用户直接就能过，过完由 [WebChromeClient.onReceivedTitle]
 * 发现并自动重走一遍解析。
 */
@SuppressLint("SetJavaScriptEnabled")
class MissavPlayActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar

    private val code by lazy { intent.getStringExtra(EXTRA_CODE).orEmpty() }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 已经交棒给播放器：之后所有探测/跳转一律停手，避免重复起播放页。 */
    private val handedOff = AtomicBoolean(false)

    /** 当前主框架 URL。`shouldInterceptRequest` 不在主线程，不能读 WebView 的成员。 */
    private val currentUrl = AtomicReference("")

    /** 当前页是不是「该番号的播放页」—— 只有播放页嗅到的 m3u8 才作数（搜索页也有广告流）。 */
    private val onPlayPage = AtomicBoolean(false)

    /** 搜索页是否已经选中并跳转过候选，防止 800ms / 1800ms 两次提取各跳一次。 */
    private val picked = AtomicBoolean(false)

    /**
     * 搜索页已经解析到第几轮。
     *
     * [decideSearchOutcome] 靠它区分「列表还在异步渲染，再等一轮」和「站点确实没有」——
     * 两轮都解析不出候选才判未收录，第一轮就判会把「还没渲染完」误报成「没收录」。
     */
    private val searchAttempt = AtomicInteger(0)

    /**
     * 已经给出终局结论（未收录 / 页面没加载出来 / 已交棒）。
     *
     * 25s 的解析超时是**无差别**兜底的：不拦住它，搜索页刚说完「资源库还未收录」，
     * 几秒后就会被一句「自动解析没成功」盖掉 —— 把明确的结论降级成含糊的。
     */
    private val settled = AtomicBoolean(false)

    private var inChallenge = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (code.isBlank()) {
            finish()
            return
        }
        setContentView(R.layout.activity_missav_play)

        statusText = findViewById(R.id.tv_missav_status)
        progressBar = findViewById(R.id.pb_missav)
        webView = findViewById(R.id.wv_missav)

        findViewById<MaterialToolbar>(R.id.toolbar_missav).setNavigationOnClickListener { finish() }

        setupWebView()
        setStatus("正在搜索「$code」…")
        webView.loadUrl(missavSearchUrl(code))
    }

    //region WebView

    private fun setupWebView() {
        with(webView) {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mediaPlaybackRequiresUserGesture = false
            // 站点本体是 HTTPS，取流也不依赖 HTTP 资源；不放宽混合内容，避免降级攻击面
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.safeBrowsingEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?
                ): Boolean = false

                /**
                 * 验证阶段**最早**能拿到「已离开验证插页」信号的时机：标题在 `<head>` 里，
                 * 远早于 `onPageFinished`（后者要等全部子资源，实测能差 6 秒）。
                 */
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!inChallenge) return
                    val url = view?.url
                    if (isMissavChallengeTitle(title) || isMissavChallengeUrl(url)) return
                    Log.i(TAG, "challenge passed, title=$title")
                    inChallenge = false
                    restartFromSearch()
                }
            }

            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val target = request?.url?.toString().orEmpty()
                    if (target.contains(".m3u8", ignoreCase = true)) {
                        onSniffed(target)
                    }
                    // 返回 null = 只旁听、不改写响应，页面照常加载
                    return null
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    val target = url.orEmpty()
                    currentUrl.set(target)
                    onPlayPage.set(
                        isMissavPlayUrl(target, code) && !isMissavSearchUrl(target)
                    )
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean = !isAllowedMissavNavigation(request?.url?.toString())

                override fun onPageFinished(view: WebView?, url: String?) {
                    val target = url.orEmpty()
                    when {
                        isMissavSearchUrl(target) -> {
                            if (picked.get()) return
                            setStatus("正在挑选资源…")
                            // 列表是异步渲染的，抓两次
                            schedule(EXTRACT_DELAY_MS) { extractSearchResults() }
                            schedule(RETRY_EXTRACT_DELAY_MS) { extractSearchResults() }
                            schedule(RESOLVE_TIMEOUT_MS) { onResolveTimeout() }
                        }

                        onPlayPage.get() -> {
                            setStatus("正在解析播放地址…")
                            scheduleProbes()
                            schedule(RESOLVE_TIMEOUT_MS) { onResolveTimeout() }
                        }
                    }
                }
            }
        }
    }

    //endregion

    //region 解析

    /**
     * 搜索结果页 → 选一条 → 打开它的播放页。
     *
     * 判定交给 [decideSearchOutcome]（纯函数，可单测），它把「解析不出候选」拆成三件事：
     * 验证插页 / 列表还没渲染完 / 站点确实没收录 —— 直接 `if (best == null)` 会把后两者
     * 混成一句含糊的失败文案。
     */
    private fun extractSearchResults() {
        if (handedOff.get() || picked.get() || settled.get()) return
        val attempt = searchAttempt.getAndIncrement()
        webView.evaluateJavascript(JS_PAGE_HTML) { raw ->
            if (handedOff.get() || picked.get()) return@evaluateJavascript
            val decision = decideSearchOutcome(unescapeJsString(raw), code, attempt)
            Log.i(TAG, "extract#${attempt + 1}: ${decision.action} candidates=${decision.candidates}")
            when (decision.action) {
                MissavResolveAction.PLAY -> {
                    val url = decision.url ?: return@evaluateJavascript
                    if (!picked.compareAndSet(false, true)) return@evaluateJavascript
                    Log.i(TAG, "extract: -> $url")
                    setStatus("找到资源，正在打开播放页…")
                    webView.loadUrl(url)
                }
                // 交给用户过一次验证，通过后 onReceivedTitle 会重走整个解析流程
                MissavResolveAction.CHALLENGE -> enterChallenge()
                // 下一轮提取（1800ms 那次）已经排好了，这一轮不需要额外动作
                MissavResolveAction.RETRY -> Unit
                MissavResolveAction.NOT_FOUND -> settle(MISSAV_NOT_FOUND_MESSAGE)
                MissavResolveAction.FALLBACK ->
                    settle("站点页面没能加载出来，可在下方页面手动操作")
            }
        }
    }

    /** 落一个终局结论，并拦住之后的无差别解析超时。 */
    private fun settle(message: String) {
        settled.set(true)
        setStatus(message)
        progressBar.visibility = View.GONE
    }

    /** 播放页里直接读 `<video>` 的 src（页面模板回退到明文时这条路能救回来）。 */
    private fun probeStream() {
        if (handedOff.get()) return
        webView.evaluateJavascript(MISSAV_STREAM_PROBE_JS) { raw ->
            val found = unescapeJsString(raw)
            Log.i(TAG, "probe: ${found.ifBlank { "(未命中)" }}")
            if (isAcceptableStreamUrl(found)) handOff(found)
        }
    }

    /** 整页兜底：在 HTML 明文里找直链。 */
    private fun deepProbe() {
        if (handedOff.get()) return
        webView.evaluateJavascript(JS_PAGE_HTML) { raw ->
            val found = extractMissavStreamUrl(unescapeJsString(raw))
            Log.i(TAG, "deepProbe: ${found ?: "(未命中)"}")
            if (found != null && isAcceptableStreamUrl(found)) handOff(found)
        }
    }

    /** 播放页探测直链的时间点：播放器常延迟注入 src，多点几次提高命中率。 */
    private fun scheduleProbes() {
        PROBE_DELAYS_MS.forEach { delay -> schedule(delay) { probeStream() } }
        schedule(DEEP_PROBE_DELAY_MS) { deepProbe() }
    }

    /**
     * 网络层嗅到的直链 —— **取流的主力手段**。
     *
     * 只在播放页采信：搜索页与推荐位同样会拉 m3u8，误采等于把广告交给播放器。
     * 回调不在主线程，状态更新必须 post 回去。
     */
    private fun onSniffed(streamUrl: String) {
        if (!onPlayPage.get()) return
        if (!isAcceptableStreamUrl(streamUrl)) return
        mainHandler.post { handOff(streamUrl) }
    }

    private fun handOff(streamUrl: String) {
        if (!handedOff.compareAndSet(false, true)) return
        Log.i(TAG, "handOff: $streamUrl")
        settled.set(true)
        setStatus("已找到播放地址，正在打开播放器…")
        progressBar.visibility = View.GONE
        // 站点页面里的播放器和我们的是同一路流, 不掐掉会和播放器一起出声
        webView.evaluateJavascript(JS_PAUSE_MEDIA, null)
        // 站点 CDN 校验来源，Referer 必须是当前播放页
        PlayerActivity.start(this, streamUrl, code, currentUrl.get())
    }

    private fun enterChallenge() {
        if (inChallenge || handedOff.get()) return
        inChallenge = true
        Log.i(TAG, "challenge: 需要人工过验证")
        setStatus("站点要求人机验证，请在页面上完成；通过后会自动继续")
    }

    private fun restartFromSearch() {
        picked.set(false)
        onPlayPage.set(false)
        // 重走一遍 = 全新的一轮，解析计数与终局标记都要清掉
        searchAttempt.set(0)
        settled.set(false)
        setStatus("验证已通过，重新解析…")
        webView.loadUrl(missavSearchUrl(code))
    }

    private fun onResolveTimeout() {
        // settled 里可能已经给了更具体的结论（未收录 / 页面没加载出来），别用含糊文案盖掉它
        if (handedOff.get() || inChallenge || settled.get()) return
        Log.w(TAG, "resolve: 超时")
        setStatus("自动解析没成功，可在下方页面手动操作")
        progressBar.visibility = View.GONE
    }

    //endregion

    private fun setStatus(text: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            statusText.text = text
        } else {
            mainHandler.post { statusText.text = text }
        }
    }

    private fun schedule(delayMs: Long, action: () -> Unit) {
        mainHandler.postDelayed(action, delayMs)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        // code 为空时 onCreate 里直接 finish(), webView 还没赋值 —— 不能无条件访问
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MissavPlay"
        private const val EXTRA_CODE = "extra_missav_code"

        /** 解析总预算。超过后不再自动跳转，把页面交给用户。 */
        private const val RESOLVE_TIMEOUT_MS = 25_000L

        /** 搜索结果页解析出候选的延迟：等列表渲染完。 */
        private const val EXTRACT_DELAY_MS = 800L

        /** 第一轮没解析出候选时的补抓延迟：列表异步渲染，再给一次机会。 */
        private const val RETRY_EXTRACT_DELAY_MS = 1_800L

        /** 播放页探测直链的时间点。 */
        private val PROBE_DELAYS_MS = listOf(1_500L, 3_500L, 6_000L, 10_000L, 15_000L)

        /** 整页兜底探测：排在最后一轮 DOM 探测之后、解析超时之前。 */
        private const val DEEP_PROBE_DELAY_MS = 18_000L

        private const val JS_PAGE_HTML =
            "(function(){return document.documentElement.outerHTML;})()"

        /** 交棒前把站点页面里的媒体元素掐掉，否则两路声音叠在一起。 */
        private const val JS_PAUSE_MEDIA =
            "(function(){try{document.querySelectorAll('video,audio').forEach(function(e){e.pause()});}catch(e){}})()"

        /**
         * 起在线播放页。[code] 是番号（如 `SSIS-001`），会拿去做站点搜索关键词。
         */
        fun start(context: Context, code: String) {
            if (code.isBlank()) return
            context.startActivity(
                Intent(context, MissavPlayActivity::class.java).apply {
                    putExtra(EXTRA_CODE, code)
                }
            )
        }
    }
}
