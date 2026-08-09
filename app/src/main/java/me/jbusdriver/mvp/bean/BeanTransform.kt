package me.jbusdriver.mvp.bean

import android.text.TextUtils
import me.jbusdriver.http.JAVBusService
import org.jsoup.nodes.Document


/**
 *movie detail
 */
fun parseMovieDetails(doc: Document): MovieDetail {
    val roeMovie = doc.select("[class=row movie]")
    val title = doc.select(".container h3").text()
    val cover = roeMovie.select(".bigImage").attr("href")

    val headers = mutableListOf<Header>()
    val headersContainer = roeMovie.select(".info")

    headersContainer.select("p[class!=star-show]:has(span:not([class=genre])):not(:has(a))")
        .mapTo(headers) {
            val split = it.text().split(":")
            Header(split.first(), split.getOrNull(1)?.trim() ?: "", "")
        } //解析普通信息

    val content = doc.select("[name=description]").attr("content")?.trim() ?: ""
    headers.add(Header("描述", content, ""))

    headersContainer.select("p[class!=star-show]:has(span:not([class=genre])):has(a)")
        .mapTo(headers) {
            val split = it.text().split(":")
            Header(
                split.first(), split.getOrNull(1)?.trim()
                    ?: "", it.select("p a").attr("href")
            )
        }//解析附带跳转信息

    val geneses = headersContainer.select(".genre:has(a[href*=genre])").map {
        Genre(it.text(), it.select("a").attr("href"))
    }//解析分类


    val actresses = doc.select("#avatar-waterfall .avatar-box").map {
        ActressInfo(it.text(), it.select("img").attr("src").wrapImage(), it.attr("href"))
    }

    val samples = doc.select("#sample-waterfall .sample-box").map {
        val thumb = it.select("img").attr("src").wrapImage()
        val image = it.attr("href")
        ImageSample(
            it.select("img").attr("title"),
            thumb,
            if (TextUtils.isEmpty(image)) thumb else image
        )
    }

val relatedMovies = doc.select("#related-waterfall .movie-box").mapNotNull {
    if (it.select(".photo-frame.bforum").isNotEmpty()) null else {
        val url = it.attr("href")
        Movie(
            it.attr("title"),
            it.select("img").attr("src").wrapImage(),
            url.split("/").last(), "", url
        )
    }
}

    headers.removeAll { it.name.contains("類別") }

    val scriptText = doc.select("script").joinToString("\n") { it.html() }
    val gid = Regex("gid\\s*=\\s*(\\d+)").find(scriptText)?.groupValues?.getOrNull(1).orEmpty()
    val uc = Regex("uc\\s*=\\s*(\\d+)").find(scriptText)?.groupValues?.getOrNull(1).orEmpty()
    val img = Regex("img\\s*=\\s*'([^']+)'").find(scriptText)?.groupValues?.getOrNull(1).orEmpty()

    val forumPosts = doc.select("#related-waterfall .movie-box").mapNotNull { post ->
        val imgEl = post.select(".photo-frame.bforum img")
        if (imgEl.isEmpty()) null else ForumPost(
            post.attr("title"),
            imgEl.attr("src"),
            post.attr("href")
        )
    }

    return MovieDetail(
        title, content, cover, gid, uc, img, headers, geneses, actresses, samples, relatedMovies, forumPosts
    )
}

/**
 * Actress
 */
fun parseActressAttrs(doc: Document): ActressAttrs {
    val frame = doc.select(".avatar-box")
    val photo = frame.select("img")
    val attrs = frame.select("p").map { it.text() }
    return ActressAttrs(
        photo.attr("title"),
        photo.attr("src").wrapImage(), attrs
    )
}

/**
 * Actress
 */
fun parseActressList(doc: Document): List<ActressInfo> {
    return doc.select(".avatar-box")?.map {
        val img = it.select("img")
        ActressInfo(
            img.attr("title"), img.attr("src").wrapImage(),
            it.attr("href"), it.select("button").text()
        )
    } ?: emptyList()
}

fun String.wrapImage() = if (this.startsWith("http")) this else JAVBusService.defaultFastUrl + this

/**
 * 论坛帖子内容
 */
fun parseForumThread(doc: Document): ForumThreadPost {
    val title = doc.select("#thread_subject").text().ifBlank { doc.title().substringBefore(" - ") }
    val floors = doc.select("div[id^=post_]").mapNotNull { postBox ->
        val postEl = postBox.select("[id^=postmessage_]").firstOrNull()
        if (postEl == null) return@mapNotNull null
        val floorNo = if (postBox.hasClass("nthread_firstpostbox")) "樓主"
        else postBox.select(".postnum_1 em").text() + "#"
        val author = postBox.select("a.xw1").text().ifBlank { "匿名" }
        val time = postBox.select("em[id^=authorposton]").text()
            .substringAfter("發表於").trim()
        val segments = parseSegments(postEl)
        ForumFloor(floorNo, author, time, segments)
    }
    android.util.Log.d("ForumParse", "title=[$title] floors=${floors.size}")

    val hasNext = doc.select("div.pg a.nxt").isNotEmpty()
    val nextUrl = if (hasNext) {
        doc.select("div.pg a.nxt").first().absUrl("href")
            .ifBlank { doc.select("div.pg a.nxt").first().attr("href") }
            .let { if (it.startsWith("http")) it else "https://www.javbus.com/forum/" + it.trimStart('/') }
    } else ""

    return ForumThreadPost(title, floors, hasNext, nextUrl)
}

private fun parseSegments(postEl: org.jsoup.nodes.Element): List<ForumSegment> {
    val segments = mutableListOf<ForumSegment>()
    val sb = StringBuilder()
    val body = postEl.clone()
    body.select("script").remove()
    body.select("br").before(" ") //br 转空格，保证文本分段
    body.childNodes().forEach { node ->
        if (node is org.jsoup.nodes.TextNode) {
            sb.append(node.text())
        } else if (node is org.jsoup.nodes.Element) {
            if (node.tagName() == "img") {
                if (sb.isNotBlank()) {
                    segments.add(ForumSegment.Text(sb.toString().trim()))
                    sb.setLength(0)
                }
                val src = node.absUrl("src")
                if (src.startsWith("http")) segments.add(ForumSegment.Image(src))
            } else {
                sb.append(node.text())
            }
        }
    }
    if (sb.isNotBlank()) segments.add(ForumSegment.Text(sb.toString().trim()))
    return segments
}