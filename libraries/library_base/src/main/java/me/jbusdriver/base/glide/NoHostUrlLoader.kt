package me.jbusdriver.base.glide

import com.bumptech.glide.integration.okhttp3.OkHttpStreamFetcher
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.*
import me.jbusdriver.base.KLog
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.base.urlPath
import java.io.InputStream


class GlideNoHostUrl(
    val url: String,
    private val noFilterHost: Set<String>,
    val providedHost: String
) {

    private fun isNeedFilter(url: String) = noFilterHost.any { url.endsWith(it) }
    fun getId() =
        ((if (isNeedFilter(url)) url else url.urlPath))  /*.apply { KLog.t("GlideNoHostUrl").d("${toStringUrl()} :$this") }*/

    override fun toString(): String = "GlideNoHostUrl(url='$url') ,id =${getId()}"


    val httpUrl by lazy {
        if (url.startsWith("http") || url.startsWith("www.")) {
            url
        } else {
            "$providedHost/${url.removePrefix("/")}"
        }
    }


}

class NoHostImageLoader(private val fac: okhttp3.Call.Factory) :
    ModelLoader<GlideNoHostUrl, InputStream> {

    private val baseHeaders: Map<String, String> = linkedMapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
        "Accept-Language" to "zh-CN,zh;q=0.9",
        "Accept-Encoding" to "gzip, deflate, br, zstd",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    )

    override fun buildLoadData(
        model: GlideNoHostUrl,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream> {

        // 每次请求新建 Builder: 复用同一个 builder 会让 Referer 跨请求不断累加
        val builder = LazyHeaders.Builder()
        (baseHeaders + ("Referer" to (model.providedHost + "/"))).forEach { (k, v) ->
            builder.addHeader(k, v)
        }
        val hostHeaders = builder.build()

        val gUrl = object : GlideUrl(model.httpUrl, hostHeaders) {
            override fun getCacheKey() = model.getId()
        }
        KLog.d("load for url $model -> $gUrl")
        return ModelLoader.LoadData(gUrl, OkHttpStreamFetcher(fac, gUrl))
    }

    override fun handles(model: GlideNoHostUrl) = true

    class Factory : ModelLoaderFactory<GlideNoHostUrl, InputStream> {
        override fun build(multiFactory: MultiModelLoaderFactory) =
            NoHostImageLoader(fac = NetClient.glideOkHttpClient)

        override fun teardown() {
        }
    }
}


