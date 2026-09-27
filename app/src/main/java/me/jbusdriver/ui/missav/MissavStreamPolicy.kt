package me.jbusdriver.ui.missav

import org.jsoup.Jsoup

/**
 * 在「已验证会话」的 WebView 里探测当前可播流地址。返回裸字符串，空串表示未找到。
 *
 * ⚠️ 三个坑都踩过，改动前先看这里：
 *
 * 1. **不能只查第一个 `<video>`**。播放页的推荐位/相关位也挂 `<video>`（悬停预览片），
 *    而且它们排在播放器前面；`document.querySelector('video')` 拿到的是预览片。
 *    所以这里遍历**全部** video/source，优先 `.m3u8`。
 * 2. **`blob:` 要丢掉**。播放器走 MSE 时 `currentSrc` 是 `blob:https://...`，
 *    对 Media3 毫无意义。只认 `http(s)://`。
 * 3. **不做整页扫描**。整页扫第一个 `.mp4` 会抓到 `fourhoi.com/<番号>/preview.mp4`
 *    这种悬停预览片（还常常是别的番号）。整页兜底交给 Kotlin 侧的
 *    [extractMissavStreamUrl]，那边能用单测覆盖。
 */
internal const val MISSAV_STREAM_PROBE_JS = """
(function(){
  try {
    function ok(u){
      if (!u) return '';
      u = String(u).trim();
      if (!/^https?:\/\//i.test(u)) return '';
      if (!/\.(m3u8|mp4)(\?|#|$)/i.test(u)) return '';
      if (/preview|\/sample|\/trailer|teaser/i.test(u)) return '';
      return u;
    }
    var vids = document.querySelectorAll('video');
    var mp4 = '';
    for (var i = 0; i < vids.length; i++) {
      var v = vids[i];
      var cands = [v.currentSrc, v.getAttribute('src'), v.src];
      var s = v.querySelector('source');
      if (s) { cands.push(s.getAttribute('src')); cands.push(s.src); }
      for (var j = 0; j < cands.length; j++) {
        var u = ok(cands[j]);
        if (!u) continue;
        if (/\.m3u8(\?|#|$)/i.test(u)) return u;
        if (!mp4) mp4 = u;
      }
    }
    return mp4;
  } catch (e) { return ''; }
})();
"""

private val STREAM_URL_RE = Regex(
    """(?:https?:)?//[^\s"'<>\\]+?\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?""",
    RegexOption.IGNORE_CASE
)

// 播放器初始化配置里可能承载直链的字段名。
private val STREAM_KEYS = listOf("source", "file", "src", "url", "hls", "hlsurl", "m3u8")

// 预先编译：原先在循环里逐个 new Regex，每次调用要重复编译 7 次。
private val STREAM_KEY_REGEXES: List<Regex> = STREAM_KEYS.map { key ->
    Regex(
        """["']?$key["']?\s*[:=]\s*["']([^"']+\.(?:m3u8|mp4)[^"']*)["']""",
        RegexOption.IGNORE_CASE
    )
}

/**
 * 广告 / 统计 / 播放器插件的流地址特征。
 *
 * 全页扫描是在整份 HTML 里抓第一个像流的 URL，页面上的贴片广告、埋点探针、
 * 预加载器都会产出 `.m3u8` / `.mp4`，不加过滤就会把广告当成正片直链交给播放器。
 */
private val AD_STREAM_MARKERS = listOf(
    "doubleclick", "googlesyndication", "googleadservices", "googletag",
    "adsystem", "adserver", "adservice", "/ads/", "/ad/", "/ad_",
    "analytics", "tracker", "tracking", "telemetry", "beacon",
    "preroll", "midroll", "postroll", "vast", "vmap",
    "popads", "propellerads", "exoclick", "juicyads", "trafficjunky",
    "adnxs", "criteo", "taboola", "outbrain"
)

/**
 * 是否是广告/统计类的流地址（仅用于全页扫描时的候选过滤）。
 *
 * 标记一律带路径分隔符前缀（`/ad_` 而不是 `ad_`）：裸 `ad_` 会误伤
 * `.../download_video.m3u8`、`.../load_720p.m3u8` 这类正常地址，
 * 而误判的代价是「真实流被拒 → 静默回退到站点页面」，很难排查。
 */
internal fun looksLikeAdStream(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return AD_STREAM_MARKERS.any { lower.contains(it) }
}

/**
 * 悬停预览片的特征。
 *
 * 播放页的推荐位/相关位挂着一堆 `<video data-src=".../preview.mp4">`：它们和正片一样是
 * `.mp4`，但只有几秒，而且**常常属于别的番号**。实测整页扫第一个 `.mp4` 时抓到过
 * `fourhoi.com/ssni-879-uncensored-leak/preview.mp4` —— 搜索的是 SSIS-001，
 * 抓到的却是 SSNI-879 的预览片。这类地址必须整体排除。
 */
private val PREVIEW_CLIP_MARKERS = listOf("preview", "/sample", "/trailer", "teaser")

internal fun looksLikePreviewClip(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    return PREVIEW_CLIP_MARKERS.any { lower.contains(it) }
}

/** 是否是可直接交给 Media3 的 HLS/MP4 地址（忽略 query / fragment）。 */
internal fun isPlayableStreamUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val path = url.substringBefore('?').substringBefore('#').trim().lowercase()
    return path.endsWith(".m3u8") || path.endsWith(".mp4")
}

/**
 * 候选直链是否可用 —— 取流的三条过滤合在一处，DOM 探测、网络嗅探、整页兜底共用。
 *
 * 三条缺一不可：只看「像不像流地址」会把推荐位的悬停预览片
 * （`fourhoi.com/<**别的番号**>/preview.mp4`）和贴片广告都当正片交给播放器。
 */
internal fun isAcceptableStreamUrl(url: String?): Boolean =
    isPlayableStreamUrl(url) &&
        !looksLikeAdStream(url.orEmpty()) &&
        !looksLikePreviewClip(url)

/** 归一 JS 里常见的转义（`\/`）、HTML 实体与协议相对地址；非 http(s) 一律判为无效。 */
internal fun normalizeStreamUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    var value = raw.trim().replace("\\/", "/").replace("&amp;", "&")
    if (value.startsWith("//")) value = "https:$value"
    if (!value.startsWith("http://") && !value.startsWith("https://")) return null
    return value
}

/**
 * 从 missav 播放页 HTML 尽力解析出可播放的 HLS/MP4 直链，解析不出返回 null。
 * 顺序：`<video>` 标签 → 播放器配置字段 → 全页扫描。
 *
 * 只有最后一步（全页扫描）会过滤广告流：`<video>` 是播放器本体、配置字段是按键名精确匹配，
 * 两者可信度高；全页扫描是兜底，噪声最大，必须剔除广告/统计地址。
 */
internal fun extractMissavStreamUrl(html: String): String? {
    if (html.isBlank()) return null
    val flat = html.replace("\\/", "/")

    val doc = Jsoup.parse(flat)
    val domSrc = doc.select("video[src]").firstOrNull()?.attr("src")
        ?: doc.select("video source[src]").firstOrNull()?.attr("src")
    normalizeStreamUrl(domSrc)?.takeIf { isPlayableStreamUrl(it) }?.let { return it }

    // 配置字段这条路也要过预览片过滤：键名 `src` 会命中 `data-src=".../preview.mp4"`，
    // 而播放页的悬停预览片正是这么写的（见 [looksLikePreviewClip]）。
    for (regex in STREAM_KEY_REGEXES) {
        regex.findAll(flat)
            .mapNotNull { normalizeStreamUrl(it.groupValues.getOrNull(1)) }
            .firstOrNull { !looksLikePreviewClip(it) }
            ?.let { return it }
    }

    return STREAM_URL_RE.findAll(flat)
        .mapNotNull { normalizeStreamUrl(it.value) }
        .firstOrNull { !looksLikeAdStream(it) && !looksLikePreviewClip(it) }
}
