package me.jbusdriver.mvp.bean

import android.text.TextUtils
import me.jbusdriver.http.BUS_SITE
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

    // 描述仍然解析出来给收藏/历史记录用, 只是详情信息区不再展示这一行
    val content = doc.select("[name=description]").attr("content")?.trim() ?: ""
    headers.add(Header("名稱", title, ""))

    val actresses = doc.select("#avatar-waterfall .avatar-box").map {
        ActressInfo(it.text(), it.select("img").attr("src").wrapImage(), it.attr("href"))
    }

    headersContainer.select("p[class!=star-show]:has(span:not([class=genre])):has(a)")
        // 女优那一行下方已有头像列表, 文字区不再重复一遍。
        // 站方把这行做成一组 /star/ 链接, 认 href 比认名字稳: 头像下的名字站点会截断
        .filterNot { p -> p.select("a").all { it.attr("href").contains("/star/") } }
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

fun String.wrapImage() = if (this.startsWith("http")) this else BUS_SITE + this
