package me.jbusdriver.mvp.bean

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

/**
 * 論壇解析。所有入口都收 (html, baseUrl): Discuz 的正文里大量 href/src 是相对路径,
 * 不带 baseUrl 解析出来的 absUrl 一律是空串, 链接和附件图会被静默丢掉。
 */

private val NOISE_URL_PARTS = listOf(
    "/static/", "/template/", "/ads/", "noavatar", "nologin", "image/common"
)

/** 站点页签条里没有「熱門主題」这一栏, 标题由本站写死 */
private const val HOT_LABEL = "熱門主題"

/**
 * 首页热帖四栏的取数口径, 站方把它们全写进了首页模板, 一律认 diy id:
 * 「熱門主題」和「精選內容」是右侧栏的两个框, 后三个页签各有自己的面板。
 * （biaoqicn2 是左侧轮播那块区域, 别混进来）
 */
private const val HOT_PANEL_ID = "biaoqicn_b_diy9"
private const val FEATURED_PANEL_ID = "biaoqicn_b_diy7"
private val HOT_TAB_PANEL_IDS = mapOf(
    "最新主題" to "biaoqicn3",
    "最新回復" to "biaoqicn4",
    "熱點話題" to "biaoqicn5"
)

fun parseForumThread(html: String, baseUrl: String): ForumThreadPost {
    val doc = Jsoup.parse(html, baseUrl)
    val title = doc.selectFirst("#thread_subject")?.text()?.trim()
        .orEmpty().ifBlank { doc.title().substringBefore(" - ") }

    // 新样式模板把楼主的作者行放在帖子盒子外面, 盒子里只剩点评列表
    val opHeader = doc.selectFirst("div.nthread_other .authi")
    val floors = doc.select("div[id^=post_]").mapNotNull { box ->
        val message = box.selectFirst("[id^=postmessage_]") ?: return@mapNotNull null
        val builder = BodyBuilder().apply { parse(message) }
        ForumFloor(
            floorNo = floorNoOf(box),
            author = authorOf(box, opHeader),
            time = timeOf(box, opHeader),
            segments = builder.segments(),
            images = builder.images()
        )
    }

    val (hasNext, nextUrl) = nextPage(doc)
    val (page, totalPage) = pageInfoOf(doc)
    return ForumThreadPost(
        title = title,
        floors = floors,
        hasNext = hasNext,
        nextUrl = nextUrl,
        pageText = pageTextOf(page, totalPage),
        page = page,
        totalPage = totalPage
    )
}

fun parseForumThreadList(html: String, baseUrl: String): ForumThreadList {
    val doc = Jsoup.parse(html, baseUrl)
    val title = doc.select("div.pt strong a, a.nvhm").firstOrNull()?.text()
        ?.ifBlank { null }
        ?: doc.title().substringBefore(" - ")

    val threads = doc.select("tbody[id*=thread_]").mapNotNull { row ->
        val link = row.select("a[href*=viewthread]")
            .firstOrNull { !it.attr("href").contains("goto=") } ?: return@mapNotNull null
        val href = link.absUrl("href").ifBlank { link.attr("href") }
        if (href.isBlank()) return@mapNotNull null
        // 板塊页的 .nums 是 div、導读页的是 span, 所以只按 class 选; 发帖时间同理在 span.dateline 里
        val nums = row.selectFirst(".nums")
        ForumThreadSummary(
            title = link.text().trim(),
            url = href,
            board = row.select("a.forumname").text().trim(),
            author = row.select("a[href*=mod=space]").firstOrNull { it.text().isNotBlank() }
                ?.text()?.trim() ?: "",
            time = row.selectFirst("span.dateline span[title]")?.attr("title")
                ?: row.selectFirst("span.dateline")?.text()?.trim()
                ?: row.selectFirst("p span[title]")?.attr("title")
                ?: row.selectFirst("p span")?.text()?.trim()
                ?: "",
            views = nums?.selectFirst("span.views")?.text()?.trim().orEmpty(),
            replies = nums?.selectFirst("span.reply")?.text()?.trim().orEmpty()
        )
    }

    val (hasNext, nextUrl) = nextPage(doc)
    return ForumThreadList(
        title, threads, hasNext, nextUrl, pageTextOf(doc),
        boardHotTabsOf(doc), sortOptionsOf(doc, baseUrl), filterOptionsOf(doc)
    )
}

/** 板塊页顶部三个信息框的容器 id, 名字与站点自己的页头文案一致 */
private val BOARD_TAB_PANELS = listOf(
    "最新主題" to "biaoqicn_b_diy6",
    "精選內容" to "biaoqicn_b_diy8",
    "精選主題" to "biaoqicn_b_diy9"
)

/** 某个框空着就整栏不显示, 站点在不同板塊给的框不完全一样 */
private fun boardHotTabsOf(doc: Element): List<ForumHotTab> =
    BOARD_TAB_PANELS.mapNotNull { (name, id) ->
        itemsIn(doc.getElementById(id)).takeIf { it.isNotEmpty() }?.let { ForumHotTab(name, it) }
    }

/**
 * 排序行（最新/熱門/熱帖/精華）站点不标当前项, 只能拿跳转地址和本页地址比。
 * 翻页会让本页地址多一个 page 参数, 比之前两边都去掉。
 */
private fun sortOptionsOf(doc: Element, baseUrl: String): List<ForumOption> {
    val current = stripPage(baseUrl)
    return doc.select("#threadlist div.tf a[href]").mapNotNull { a ->
        val url = a.absUrl("href")
        val label = a.text().trim()
        if (url.isBlank() || label.isBlank()) return@mapNotNull null
        ForumOption(label, url, stripPage(url) == current)
    }
}

/** 分类筛选: 当前项由站点标在 li 的 class a 上; 条数在链接内的 span.num 里, 不能留在标签上 */
private fun filterOptionsOf(doc: Element): List<ForumOption> =
    doc.select("#thread_types li a[href]").mapNotNull { a ->
        val li = a.parent() ?: return@mapNotNull null
        val url = a.absUrl("href")
        val label = a.text().trim().removeSuffix(a.selectFirst("span.num")?.text()?.trim().orEmpty()).trim()
        if (url.isBlank() || label.isBlank()) return@mapNotNull null
        ForumOption(label, url, li.hasClass("a"))
    }

private fun stripPage(url: String): String =
    url.substringBefore('#').replace(Regex("[&?]page=[0-9]+"), "")

/**
 * 論壇首頁: 左輪播 + 右熱帖頁簽 + 分組板塊。
 * 站點把這幾個區塊全寫在首頁 HTML 里, 頁簽切換只是 display:none, 所以一次請求就夠。
 */
fun parseForumHome(html: String, baseUrl: String): ForumHome {
    val doc = Jsoup.parse(html, baseUrl)
    return ForumHome(slidesOf(doc), hotTabsOf(doc), boardGroupsOf(doc))
}

private fun slidesOf(doc: Element): List<ForumSlide> =
    doc.select("ul.slideshow > li").mapNotNull { li ->
        val link = li.selectFirst("a[href*=viewthread]") ?: return@mapNotNull null
        val url = link.absUrl("href")
        if (url.isBlank()) return@mapNotNull null
        val img = li.selectFirst("img") ?: return@mapNotNull null
        val image = img.absUrl("src")
        if (image.isBlank()) return@mapNotNull null
        // p.biaoqicn_title 被站点截过, alt 才是完整标题
        ForumSlide(
            title = img.attr("alt").trim().ifBlank { li.selectFirst("p")?.text()?.trim() ?: "" },
            image = image,
            link = url
        )
    }

/**
 * 站点的页签条只有三栏（最新主題/最新回復/熱點話題, 面板依次是 biaoqicn3/4/5）,
 * 「熱門主題」不是站点页签, 它的数据在右侧栏, 所以这一栏由本站拼出来放最前。
 * 认不出名字的页签宁可少显示一个, 也别按位置去凑——站点哪天加栏, 位置就会整体错位。
 */
private fun hotTabsOf(doc: Element): List<ForumHotTab> {
    val siteTabs = doc.select("div.new4_list_top li").mapNotNull { li ->
        val name = li.text().trim()
        val panelId = HOT_TAB_PANEL_IDS[name] ?: return@mapNotNull null
        ForumHotTab(name, itemsIn(doc.getElementById(panelId)))
    }
    return (listOf(ForumHotTab(HOT_LABEL, hotTabItemsOf(doc))) + siteTabs).distinctBy { it.name }
}

/** 熱門主題栏 = 站点自己的熱門主題框在前, 站方人工挑的精選內容补在后面, 同一条帖子不重复出现 */
private fun hotTabItemsOf(doc: Element): List<ForumHotItem> =
    (itemsIn(doc.getElementById(HOT_PANEL_ID)) + itemsIn(doc.getElementById(FEATURED_PANEL_ID)))
        .distinctBy { forumTidOf(it.link) }

/**
 * 这些框的条目结构各不相同, 每个容器只命中其中一种:
 * 首页页签面板是 h3.biaoqicn_listN > a, 首頁熱門主題框是 li > p.comment-post > a,
 * 首頁精選內容框是 dl > dt > a, 板塊页最新主題框是 ul.biaoqicn_bjctj > li > a(带 rank 序号),
 * 板塊页精選內容框是 div.main-right-kuaixu-txt > a(同一条的封面图另有一个没文字的链接)。
 * 一条帖子可能带三个链接(封面图/标题/摘要), 只取有标题的那个, 剩下的靠 tid 去重。
 */
private fun itemsIn(container: Element?): List<ForumHotItem> {
    container ?: return emptyList()
    val links = container.select(
        "h3 a[href*=viewthread], dt > a[href*=viewthread], li p.comment-post > a[href*=viewthread], " +
            "ul.biaoqicn_bjctj > li > a[href*=viewthread], div.main-right-kuaixu-txt > a[href*=viewthread]"
    )
    return links.mapNotNull { hotItemOf(it) }.distinctBy { forumTidOf(it.link) }
}

private fun hotItemOf(link: Element): ForumHotItem? {
    val url = link.absUrl("href")
    if (url.isBlank()) return null
    val title = link.attr("title").trim().ifBlank { link.text().trim() }
    if (title.isBlank()) return null
    return ForumHotItem(title, url)
}

private fun boardGroupsOf(doc: Element): List<ForumBoardGroup> =
    doc.select("div.fl.bm").mapNotNull { area ->
        val name = area.selectFirst("div.bm_h h2 a")?.text()?.trim() ?: return@mapNotNull null
        val boards = area.select("table.fl_tb tr").mapNotNull { tr ->
            val a = tr.selectFirst("td h2 a[href*=forumdisplay]") ?: return@mapNotNull null
            val url = a.absUrl("href")
            if (url.isBlank()) return@mapNotNull null
            val title = a.text().trim()
            if (title.isBlank()) return@mapNotNull null
            ForumEntry(title, url, tr.selectFirst("td p.xg2")?.text()?.trim().orEmpty())
        }
        if (boards.isEmpty()) null else ForumBoardGroup(name, boards)
    }

private fun floorNoOf(box: Element): String {
    if (box.hasClass("nthread_firstpostbox")) return "樓主"
    // 普通楼层的锚点是 <a id=postnum..><em>N</em><sup>#</sup></a>;
    // 置顶回复的锚点没有 em, 文本是 "來自 N#", 标不出自己的楼号
    return box.select("a[id^=postnum] em").firstOrNull()?.text()?.trim()?.plus("#") ?: "置頂"
}

private fun authorOf(box: Element, opHeader: Element?): String {
    if (box.hasClass("nthread_firstpostbox")) {
        opHeader?.selectFirst("a")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
    }
    // 限定在左栏作者区, 否则会抓到点评/回复列表里的第一个名字
    return box.selectFirst("td.pls a.xw1")?.text()?.trim()
        ?: box.selectFirst(".authi a")?.text()?.trim()
        ?: box.selectFirst("a.xw1")?.text()?.trim()
        ?: "匿名"
}

private fun timeOf(box: Element, opHeader: Element?): String {
    if (box.hasClass("nthread_firstpostbox")) {
        opHeader?.selectFirst("span")?.text()?.trim()?.let { if (it.isNotBlank()) return it }
    }
    val raw = box.select("em[id^=authorposton]").firstOrNull()?.text()
        ?.ifBlank { null }
        ?: box.select("p.postinfo span[title]").firstOrNull()?.attr("title")
        ?: box.select("td.pm em span[title]").firstOrNull()?.attr("title")
        ?: return ""
    return raw.substringAfter("發表於").trim()
}

/**
 * div.pg 里除了真正的下一页, 还有每条回复"展开更多"用的 javascript:; 链接, 必须跳过
 */
private fun nextPage(doc: Element): Pair<Boolean, String> {
    val next = doc.select("a.nxt").firstOrNull {
        val href = it.attr("href")
        href.isNotBlank() && !href.startsWith("javascript")
    } ?: return false to ""
    return true to next.absUrl("href").ifBlank { next.attr("href") }
}

/**
 * 当前页是 div.pg 里那个纯数字的 strong, 总页数在跳转输入框的 label 上 ("共 15 頁" / "/ 15 頁")。
 * 拿不到总页数时按单页返回, 上层据此决定要不要挂分页控件。
 */
private fun pageInfoOf(doc: Element): Pair<Int, Int> {
    val current = doc.select("div.pg strong").map { it.text().trim() }
        .firstOrNull { it.isNotEmpty() && it.all { c -> c.isDigit() } }?.toIntOrNull() ?: 1
    val total = doc.selectFirst("div.pg label span")?.let { sp ->
        Regex("""(\d+)""").find("${sp.attr("title")} ${sp.text()}")?.groupValues?.getOrNull(1)
    }?.toIntOrNull() ?: current
    return current to maxOf(current, total)
}

private fun pageTextOf(doc: Element): String {
    val (current, total) = pageInfoOf(doc)
    return pageTextOf(current, total)
}

private fun pageTextOf(current: Int, total: Int): String =
    if (total > current) "$current / $total" else "$current"

/**
 * 正文 -> 有序内容段。换行按 <br>/<p>/<div> 还原, 链接和粗斜体用 span 表达,
 * 这样列表里一个 TextView 就是一段, 排版不再被压成一行。
 */
private class BodyBuilder {

    private val segments = ArrayList<ForumSegment>()
    private val images = ArrayList<String>()
    private val buf = SpannableStringBuilder()

    fun parse(message: Element) {
        val body = message.clone()
        body.select("script, style, .pstatus, .attn, .locked, .attach_nopermission, .showhide, .fullfoot")
            .remove()
        walkChildren(body)
        flush()
    }

    fun segments(): List<ForumSegment> = segments

    fun images(): List<String> = images

    private fun walkChildren(el: Element) {
        for (node in el.childNodes()) {
            when (node) {
                is TextNode -> buf.append(node.text())
                is Element -> walk(node)
                else -> Unit
            }
        }
    }

    private fun walk(el: Element) {
        when (el.tagName()) {
            "br" -> newline()
            "img" -> image(el)
            "blockquote" -> quote(el)
            "a" -> styled(el, el.hrefSpan())
            "b", "strong" -> styled(el, StyleSpan(Typeface.BOLD))
            "i", "em", "cite" -> styled(el, StyleSpan(Typeface.ITALIC))
            "u" -> styled(el, UnderlineSpan())
            "script", "style" -> Unit
            "p", "div", "li", "dl", "dt", "dd", "tr", "table", "center",
            "h1", "h2", "h3", "h4", "font", "span", "td" -> {
                walkChildren(el)
                newline()
            }
            else -> walkChildren(el)
        }
    }

    /**
     * 段落/图片可能在中途 flush 掉 buf, 所以写 span 前要确认区间还活着
     */
    private fun styled(el: Element, span: CharacterStyle?) {
        val from = buf.length
        walkChildren(el)
        val to = buf.length
        if (span != null && to > from && to <= buf.length) {
            buf.setSpan(span, from, to, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun Element.hrefSpan(): CharacterStyle? {
        val href = absUrl("href").ifBlank { attr("href") }
        // 站内跳转/评分/引用回复大多是 javascript: 或纯锚点, 挂上去只会点了没反应
        if (!href.startsWith("http")) return null
        return URLSpan(href)
    }

    private fun quote(el: Element) {
        val header = el.select("> em").firstOrNull()?.text()?.trim().orEmpty()
        val clone = el.clone()
        clone.select("> em").firstOrNull()?.remove()
        newline()
        val inner = BodyBuilder().apply { parse(clone) }
        flush()
        inner.segments.forEach { segment ->
            when (segment) {
                is ForumSegment.Text -> segments.add(ForumSegment.Quote(header, segment.content))
                is ForumSegment.Quote -> segments.add(segment)
                is ForumSegment.Image -> addImage(segment.thumb, segment.full)
            }
        }
        newline()
    }

    private fun image(el: Element) {
        val thumb = el.absUrl("src").ifBlank { el.attr("src") }
        if (thumb.isBlank() || NOISE_URL_PARTS.any { thumb.contains(it, true) }) return
        val full = el.absUrl("zoomfile").ifBlank { el.absUrl("file") }
            .ifBlank { thumb.removeSuffix(".thumb.jpg") }
        addImage(thumb, full)
    }

    private fun addImage(thumb: String, full: String) {
        flush()
        images.add(full)
        segments.add(ForumSegment.Image(thumb, full, images.lastIndex))
    }

    /**
     * 连续 <br /> 只算一次换行: 帖子源码里空行成对出现, 原样还原会撑出大片空白。
     * 换行前顺手吃掉行尾空格, 免得裁切时留下 "xxx \n"。
     */
    private fun newline() {
        var end = buf.length
        while (end > 0 && buf[end - 1] == ' ') end--
        if (end == 0) return
        if (end < buf.length) buf.delete(end, buf.length)
        if (buf[end - 1] != '\n') buf.append('\n')
    }

    private fun flush() {
        val text = buf.trimSpans()
        buf.clear()
        if (text != null) segments.add(ForumSegment.Text(text))
    }

    /** 直接 toString 再 trim 会把 span 一起丢掉, 所以自己裁两端空白并平移 span 偏移 */
    private fun SpannableStringBuilder.trimSpans(): CharSequence? {
        val source = this
        var start = 0
        var end = source.length
        while (start < end && source[start].isWhitespace()) start++
        while (end > start && source[end - 1].isWhitespace()) end--
        if (start == end) return null
        val out = SpannableStringBuilder(source.subSequence(start, end))
        source.getSpans(start, end, Any::class.java).forEach { span ->
            val from = source.getSpanStart(span)
            val to = source.getSpanEnd(span)
            if (to > start && from < end) {
                out.setSpan(
                    span,
                    (from - start).coerceAtLeast(0),
                    (to - start).coerceAtMost(out.length),
                    source.getSpanFlags(span)
                )
            }
        }
        return out
    }
}
