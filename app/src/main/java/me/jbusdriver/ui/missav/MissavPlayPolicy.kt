package me.jbusdriver.ui.missav

import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import org.jsoup.Jsoup

internal data class MissavSearchResult(
    val url: String,
    val title: String,
    val duration: String? = null,
    val badge: String? = null,
    val thumbnailUrl: String? = null,
    /**
     * 是否无码（站点挂了「无码流出」变体，或卡片上标了 `Uncensored`）。
     *
     * 单独存一个布尔值而不是靠 [badge] 反推：badge 是展示文案（还可能被「中字」挤掉），
     * 而选片逻辑要的是一个稳定的语义标记。
     */
    val uncensored: Boolean = false
)

internal fun normalizeMissavCode(code: String): String =
    code.trim().lowercase().replace(" ", "")

internal fun missavSearchUrl(code: String): String {
    val query = code.trim()
    val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
    return "https://missav.ws/en/search/$encoded"
}

internal fun isMissavSearchUrl(url: String?): Boolean {
    val path = urlPath(url) ?: return false
    return path.contains("/search/")
}

/**
 * 人机验证页的**强**特征。
 *
 * 判据只取验证插页自身独有的标记，不要用 "cdn-cgi/challenge" / "cf-challenge" /
 * "challenges.cloudflare.com" 这类子串：Cloudflare 的 JS Detections 会把
 * `/cdn-cgi/challenge-platform/scripts/jsd/main.js` 注入到**每一个正常页面**里，
 * 拿它当判据会把已通过验证的正常搜索结果页误判成验证页，
 * 于是自动接管被静默跳过、直接掉进回退分支（表现为「明明搜到了结果却不播」）。
 */
private val CHALLENGE_HTML_MARKERS = listOf(
    "正在进行安全验证",
    "just a moment",
    "challenge-running",
    "challenge-form",
    "cf-turnstile",
    "chl_page"
)

internal fun isMissavChallengeHtml(html: String): Boolean {
    val lower = html.lowercase()
    return CHALLENGE_HTML_MARKERS.any { it in lower }
}

internal fun isMissavChallengeTitle(title: String?): Boolean {
    val text = title.orEmpty()
    val lower = text.lowercase()
    return "请稍候" in text ||
        "安全验证" in text ||
        "just a moment" in lower ||
        text.equals("missav.ws", ignoreCase = true)
}

internal fun isMissavChallengeUrl(url: String?): Boolean {
    val text = url.orEmpty().lowercase()
    return "__cf_chl" in text ||
        "cdn-cgi/challenge" in text ||
        "challenges.cloudflare.com" in text
}

// 允许的站内域名表：missav 换镜像是常事，加新镜像改这里即可。
internal val MISSAV_SITE_HOSTS = listOf("missav.ws", "missav.com")
internal val CLOUDFLARE_HOSTS = listOf("cloudflare.com")

internal fun missavHost(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        URI(url).host?.lowercase()?.removePrefix("www.")
    } catch (_: Exception) {
        null
    }
}

internal fun isMissavSiteUrl(url: String?): Boolean {
    val host = missavHost(url) ?: return false
    return MISSAV_SITE_HOSTS.any { host == it || host.endsWith(".$it") }
}

internal fun isCloudflareUrl(url: String?): Boolean {
    val host = missavHost(url) ?: return false
    return CLOUDFLARE_HOSTS.any { host == it || host.endsWith(".$it") }
}

internal const val MISSAV_CLEAN_PLAY_JS = """
(function(){
  function junk(el) {
    if (!el || !el.tagName) return false;
    if (el.querySelector && el.querySelector('video')) return false;
    var id = (el.id || '').toLowerCase();
    var cls = (el.className || '').toString().toLowerCase();
    var tag = el.tagName.toLowerCase();
    if (tag === 'video' || tag === 'source') return false;
    if (tag === 'iframe') {
      var src = (el.getAttribute('src') || '').toLowerCase();
      return src.indexOf('missav') < 0 && src.indexOf('cloudflare') < 0;
    }
    return cls.indexOf('ad-') >= 0 || cls.indexOf('ads') >= 0 || cls.indexOf('popup') >= 0 ||
      cls.indexOf('banner') >= 0 || cls.indexOf('recommend') >= 0 || cls.indexOf('related') >= 0 ||
      cls.indexOf('watch-next') >= 0 || tag === 'nav' || tag === 'footer' || tag === 'aside';
  }
  function hideJunk() {
    Array.prototype.slice.call(document.querySelectorAll('iframe, aside, nav, footer')).forEach(function(el){
      if (!junk(el)) return;
      el.style.setProperty('display','none','important');
      el.style.setProperty('pointer-events','none','important');
    });
  }
  hideJunk();
  if (!window.__javcinemaCleanObs) {
    window.__javcinemaCleanObs = true;
    new MutationObserver(hideJunk).observe(document.documentElement, {childList:true, subtree:true});
  }
})();
"""

/**
 * 让站点的结果卡片在触摸设备上能点开。
 *
 * 站点把整张卡片（**含标题链接**）包在
 * `<div class="thumbnail group" @click="clickPreview(...)">` 里，这是给桌面 hover 写的：
 * 触摸时 Alpine 的 `@click` 会把锚点的默认跳转 preventDefault 掉，而 `clickPreview`
 * 依赖 `@mouseenter` 先设好的状态，在触摸设备上什么也不做 ——
 * 表现就是「回退到站点页面后，点结果卡片毫无反应」。
 *
 * 这里在**捕获阶段**先接管站内链接：`stopPropagation` 让 Alpine 收不到事件，再自己跳转。
 * 只接管站内 http(s) 链接，并跳过 `href="#"` 这类原地链接 ——
 * 站点用它挂菜单/搜索开关，接管了反而会把菜单点坏。
 */
internal const val MISSAV_CLICK_FIX_JS = """
(function(){
  if (window.__javcinemaClickFix) return;
  window.__javcinemaClickFix = true;
  function base(u){ return String(u || '').split('#')[0]; }
  document.addEventListener('click', function(e){
    var t = e.target;
    var a = (t && t.closest) ? t.closest('a[href]') : null;
    if (!a) return;
    var href = a.href || '';
    if (!/^https?:\/\//i.test(href)) return;
    if (!/^https?:\/\/[^\/]*missav\./i.test(href)) return;
    if (base(href) === base(location.href)) return;
    e.preventDefault();
    e.stopPropagation();
    location.href = href;
  }, true);
})();
"""

internal fun isAllowedMissavNavigation(url: String?, mainFrame: Boolean = true): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    if (lower.startsWith("about:") || lower.startsWith("blob:") || lower.startsWith("data:")) {
        return true
    }
    // 主帧只放行站内与 Cloudflare 人机验证。注意：isMissavChallengeUrl 是子串匹配，
    // 不能单独作为放行条件，否则 evil.com?__cf_chl=1 也能绕过。
    if (isMissavSiteUrl(url) || isCloudflareUrl(url)) {
        return true
    }
    if (!mainFrame) return true
    return false
}

internal fun isMissavPlayUrl(url: String, code: String): Boolean {
    val needle = normalizeMissavCode(code)
    if (needle.isEmpty()) return false
    val path = urlPath(url)?.lowercase() ?: return false
    if ("/search/" in path || "/actresses" in path || "/genres" in path || "/makers" in path) {
        return false
    }
    val slug = urlSlug(path) ?: return false
    return slugMatchesCode(slug, needle)
}

/**
 * 番号是否作为**完整一段**出现在 slug 里（前后不能再贴字母或数字）。
 *
 * 为什么不能只比「slug == 番号」和「番号- 开头」：**无码番号在站点上必然挂着片商前缀**。
 * 实测（2026-09-25，番号 `092326_01`，站点搜索页 dump 出来的播放链接是
 * `https://missav.ws/en/musume-092326_01`）—— 旧判据下这条候选直接被丢，
 * 候选数 0 就成了「资源库还未收录，播放失败」，而结果卡片就在用户眼前；
 * 顺带也让 `onPageFinished` 认不出这是播放页，回退站点后再也接管不回来。
 * 骑兵能播、步兵全军覆没，差别就在这一个前缀上。
 *
 * 收紧的方式是**按分隔符成段**比对，而不是放宽成子串：
 * `092326_01` 命中 `musume-092326_01`、`musume-092326_01-chinese-subtitle`，
 * 但 `879` 不会命中 `ssni-8791`（后面贴了数字），`pppe-443` 也不会命中 `pppe-437`。
 */
private fun slugMatchesCode(slug: String, needle: String): Boolean {
    var from = 0
    while (true) {
        val at = slug.indexOf(needle, from)
        if (at < 0) return false
        val after = at + needle.length
        val leftOk = at == 0 || !slug[at - 1].isLetterOrDigit()
        val rightOk = after == slug.length || !slug[after].isLetterOrDigit()
        if (leftOk && rightOk) return true
        from = at + 1
    }
}

/** URL 路径的最后一段（slug），统一小写；取不到或为空返回 null。 */
internal fun missavSlug(url: String?): String? = urlSlug(urlPath(url))

private fun urlSlug(path: String?): String? =
    path?.lowercase()?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

/**
 * 从搜索结果里挑一条用于自动接管。
 *
 * **无码优先**：同一部片站点常同时挂「无码流出」变体和原版，多个候选时优先播无码。
 * 无码候选内部仍先取「就是番号本体」的那条、再退站点排序第一条；
 * 没有无码候选时才回到同样的顺序。
 *
 * 能走到这里的候选都已通过 [isMissavPlayUrl] 校验（番号在 slug 里成段出现），
 * 所以候选之间只差版本，不会串到别的番号。
 */
internal fun selectBestMissavResult(
    results: List<MissavSearchResult>,
    code: String
): MissavSearchResult? {
    if (results.isEmpty()) return null
    val needle = normalizeMissavCode(code)
    if (needle.isEmpty()) return results.first()
    fun pick(list: List<MissavSearchResult>): MissavSearchResult {
        // 骑兵的 slug 就是番号本体；无码必然带片商前缀（见 [slugMatchesCode]），
        // 所以本体那条退化成「尾巴是番号」，避免在有原版可选时选中挂了版本后缀的变体。
        val bare = list.firstOrNull { missavSlug(it.url) == needle }
            ?: list.firstOrNull { missavSlug(it.url)?.endsWith(needle) == true }
        return bare ?: list.first()
    }
    val uncensored = results.filter { it.uncensored }
    return pick(if (uncensored.isNotEmpty()) uncensored else results)
}

/**
 * 解析搜索结果页。只负责解析，不判人机验证 —— 是否验证页交给调用方在
 * 「解析不出候选」之后判断。顺序反过来会把正常页面误判成验证页（见 [isMissavChallengeHtml]）。
 *
 * ⚠️ **疑似广告的卡片只降级、不丢弃**：只有「除它之外一条候选都没有」时才回退用它。
 * 真实事故（UZU-040）：站点的英文长简介里有一句
 * "She **lives** in the same apartment building"，而 [isAdOrJunkTitle] 当时用
 * `"live" in title` 做**子串**判断 → 唯一的候选被误杀 → 站点明明有这部片，
 * 却给用户报「资源库还未收录，播放失败」。广告规则再准也可能误伤，
 * 所以最后必须留一条「宁可带上疑似广告，也不清空结果」的兜底。
 */
internal fun parseMissavSearchResults(html: String, code: String): List<MissavSearchResult> {
    val doc = Jsoup.parse(html, "https://missav.ws")
    val grouped = linkedMapOf<String, MutableList<org.jsoup.nodes.Element>>()
    for (anchor in doc.select("a[href]")) {
        val raw = anchor.absUrl("href").ifBlank { anchor.attr("href") }
        val url = canonicalizeMissavUrl(raw) ?: continue
        if (!isMissavPlayUrl(url, code)) continue
        grouped.getOrPut(url) { mutableListOf() }.add(anchor)
    }
    val cards = grouped.map { (url, anchors) -> toCard(url, anchors) }
    val clean = cards.filter { !it.junkTitle }
    return clean.ifEmpty { cards }.map { it.result }
}

/** 一条候选卡片，以及它的标题是否被广告规则命中（命中时仅作兜底用）。 */
private data class MissavCard(val result: MissavSearchResult, val junkTitle: Boolean)

private fun toCard(url: String, anchors: List<org.jsoup.nodes.Element>): MissavCard {
    val texts = anchors.map { it.text().trim() }.filter { it.isNotEmpty() }
    val img = anchors.mapNotNull { it.selectFirst("img") }.firstOrNull()
    val imgAlt = img?.attr("alt")?.trim().orEmpty()
    val thumbnail = sequenceOf(
        img?.absUrl("data-src"),
        img?.attr("data-src"),
        img?.absUrl("src"),
        img?.attr("src")
    ).map { it?.trim().orEmpty() }.firstOrNull { it.startsWith("http") }
    val duration = texts.firstNotNullOfOrNull { DURATION_RE.find(it)?.value }
    val title = listOf(imgAlt, texts.firstOrNull { candidate ->
        candidate != duration &&
            !candidate.equals("Uncensored", ignoreCase = true) &&
            !candidate.equals("HD", ignoreCase = true) &&
            candidate.length > 8
    }).firstOrNull { !it.isNullOrBlank() } ?: url.substringAfterLast('/')
    val joined = texts.joinToString(" ")
    val uncensored = url.contains("uncensored-leak", ignoreCase = true) ||
        joined.contains("Uncensored", ignoreCase = true)
    val badge = when {
        uncensored -> "无码"
        url.contains("chinese-subtitle", ignoreCase = true) ||
            joined.contains("Chinese", ignoreCase = true) -> "中字"
        else -> null
    }
    return MissavCard(
        result = MissavSearchResult(
            url = url,
            title = title,
            duration = duration,
            badge = badge,
            thumbnailUrl = thumbnail,
            uncensored = uncensored
        ),
        junkTitle = isAdOrJunkTitle(title)
    )
}

/**
 * 卡片标题是否像广告 / 站点导航链接。
 *
 * ⚠️ **必须按词匹配，不能用 `"live" in title` 这类子串判断** —— 站点搜索页的侧栏挂着
 * 两条导航链接 "Korean Live" / "Chinese Live"（指向 `/en/klive`、`/en/clive`），
 * 子串判断确实能滤掉它们，但代价是把所有含 live 子串的**正常英文标题**一起杀掉：
 * `lives` / `believe` / `delivered` / `alive` 全中招（真实事故见
 * [parseMissavSearchResults] 的注释）。按词匹配两边都能顾上。
 *
 * 残留风险：真有片名里带独立单词 "Live" 的（如 "Live Streaming …"）仍会被判为广告，
 * 但只要它是唯一候选，[parseMissavSearchResults] 的兜底会把它留下。
 */
private val JUNK_TITLE_RE = Regex("""\b(?:live|webcam|myavlive)\b""", RegexOption.IGNORE_CASE)

private fun isAdOrJunkTitle(title: String): Boolean =
    JUNK_TITLE_RE.containsMatchIn(title) ||
        title.equals("Uncensored", ignoreCase = true) ||
        DURATION_RE.matches(title)

private val DURATION_RE = Regex("""\d+:\d{2}(?::\d{2})?""")

/**
 * 解开 `WebView.evaluateJavascript` 回传的 JSON 字符串字面量。
 *
 * ⚠️ 必须按 JSON 规范解**全部**转义，尤其是 `\uXXXX`：Android 会把结果里的
 * `<` `>` `&` `=` `'` 转义成 `\u003C` 这类 Unicode 转义（防止页面内容夹带标签）。
 * 只 replace `\n` / `\"` / `\/` 的话，拿到的 HTML 是满屏 `\u003Cdiv>` ——
 * Jsoup 一个标签都认不出来，搜索页永远解析出 0 条候选，自动接管被静默跳过
 * （现象是「明明搜到了结果却不播」，且直接掉进回退分支）。
 *
 * 交给 Gson 是因为它按规范处理全部转义（含 UTF-16 代理对），比手工 replace 链可靠。
 */
internal fun unescapeJsString(value: String?): String {
    if (value.isNullOrBlank() || value == "null") return ""
    return try {
        val parsed = JsonParser.parseString(value)
        if (parsed.isJsonPrimitive) parsed.asString else value
    } catch (_: Exception) {
        // evaluateJavascript 只会回传合法 JSON，走到这里说明是异常输入，原样返回。
        value
    }
}

private fun canonicalizeMissavUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("javascript:", ignoreCase = true)) {
        return null
    }
    val absolute = when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://${MISSAV_SITE_HOSTS.first()}$trimmed"
        else -> trimmed
    }
    return try {
        val uri = URI(absolute)
        val host = uri.host?.lowercase() ?: return null
        if (MISSAV_SITE_HOSTS.none { host == it || host.endsWith(".$it") }) return null
        URI(uri.scheme, host, uri.path, null, null).toString().trimEnd('/')
    } catch (_: Exception) {
        null
    }
}

private fun urlPath(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        URI(url).path
    } catch (_: Exception) {
        url
    }
}
