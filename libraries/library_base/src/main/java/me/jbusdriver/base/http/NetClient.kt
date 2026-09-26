package me.jbusdriver.base.http

import android.content.Context
import android.net.ConnectivityManager
import com.google.gson.JsonObject
import me.jbusdriver.base.BuildConfig
import me.jbusdriver.base.GSON
import me.jbusdriver.base.KLog
import okhttp3.*
import retrofit2.CallAdapter
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.lang.reflect.Type
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


/**
 * Created by Administrator on 2016/7/22 0022.
 */
object NetClient {
    private const val TAG = "NetClient"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.67 Safari/537.36"
    // private val gsonConverterFactory = GsonConverterFactory.create(GSON)

    private val EXIST_MAGNET_INTERCEPTOR by lazy {
        Interceptor { chain ->
            val request = chain.request()
            // 站点接口用 existmag 头表达"是否包含已有磁力", 在这里翻译成站点认的 Cookie
            val existmag = if (request.header("existmag") == "all") "all" else "mag"
            val cookie = "existmag=$existmag" +
                    ";bus_auth=4b85UbbfIo1f9unsrObLRtu0aYAe8VOgu7OjJJBPE95b9jKg0Jqj7xGmCEzb9VJOGoJO"
            // 追加而不是整体覆盖: 站点下发的会话 Cookie 必须保留
            val existing = request.header("Cookie")
            val builder = request.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .removeHeader("existmag")
                .header("Cookie", if (existing.isNullOrEmpty()) cookie else "$existing; $cookie")
            chain.proceed(builder.build())
        }
    }
    val RxJavaCallAdapterFactory: CallAdapter.Factory = RxJava2CallAdapterFactory.create()
    val PROGRESS_INTERCEPTOR by lazy {
        Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val body = response.body ?: return@Interceptor response
            return@Interceptor response.newBuilder()
                .body(ProgressResponseBody(request.url.toString(), body, GlobalProgressListener))
                .build()
        }
    }

    private val strConv = object : Converter.Factory() {

        override fun requestBodyConverter(
            type: Type,
            parameterAnnotations: Array<Annotation>,
            methodAnnotations: Array<Annotation>,
            retrofit: Retrofit
        ): Converter<*, RequestBody>? {
            KLog.d("requestBodyConverter", type, parameterAnnotations, methodAnnotations)
            for (parameterAnnotation in parameterAnnotations) {
                KLog.d("parameterAnnotation", parameterAnnotation)
            }
            return super.requestBodyConverter(
                type,
                parameterAnnotations,
                methodAnnotations,
                retrofit
            )
        }

        override fun responseBodyConverter(
            type: Type?,
            annotations: Array<out Annotation>?,
            retrofit: Retrofit?
        ): Converter<ResponseBody, *> =
            Converter<ResponseBody, String> { it.string() }
    }

    private val jsonConv = object : Converter.Factory() {
        override fun responseBodyConverter(
            type: Type?,
            annotations: Array<out Annotation>?,
            retrofit: Retrofit?
        ): Converter<ResponseBody, *> =
            Converter<ResponseBody, JsonObject> {
                val s = it.string()
                val json = GSON.fromJson(s, JsonObject::class.java)
                if (json == null || json.isJsonNull || json.entrySet().isEmpty()) {
                    error("json is null")
                }
                if (json.get("code")?.asInt == 200) {
                    return@Converter json
                } else {
                    error(json.get("message")?.asString ?: "未知错误")
                }
            }
    }

    fun getRetrofit(
        // 默认值跟着版本信息源走: raw.githubusercontent.com 实测直连超时, 别再用它
        baseUrl: String = "https://cdn.jsdelivr.net/",
        handleJson: Boolean = false,
        client: OkHttpClient = okHttpClient
    ): Retrofit =
        Retrofit.Builder().client(client).apply {
            if (baseUrl.isNotEmpty()) this.baseUrl(baseUrl)
        }.addConverterFactory(if (handleJson) jsonConv else strConv)
            .addCallAdapterFactory(RxJavaCallAdapterFactory)
            .build()

    //endregion

    private val okHttpClient by lazy {
        //设置缓存路径

        // .addNetworkInterceptor(StethoInterceptor())
        val client = OkHttpClient.Builder()
            .writeTimeout((30 * 1000).toLong(), TimeUnit.MILLISECONDS)
            .readTimeout((20 * 1000).toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout((15 * 1000).toLong(), TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(EXIST_MAGNET_INTERCEPTOR)
            .cookieJar(object : CookieJar {
                // OkHttp 会从连接池的多个线程回调这里, 不能用 HashMap
                private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    cookieStore[url.host] = cookies
                }

                override fun loadForRequest(url: HttpUrl) = cookieStore[url.host] ?: emptyList()
            })
        if (BuildConfig.DEBUG) {
            client.addInterceptor(LoggerInterceptor("OK_HTTP"))
        }
        client.build()
    }

    val glideOkHttpClient: OkHttpClient by lazy {
        // 图片域名不需要站点 Cookie, 更不能把 bus_auth 凭据带出去
        val client = OkHttpClient.Builder()
            .readTimeout((20 * 1000).toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout((15 * 1000).toLong(), TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(PROGRESS_INTERCEPTOR)
        if (BuildConfig.DEBUG) {
            client.addInterceptor(LoggerInterceptor("OK_HTTP"))
        }
        client.build()
    }

    /**
     * 判断是否有网络可用

     * @param context
     * *
     * @return
     */
    fun isNetAvailable(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetworkInfo?.isAvailable ?: false
    } catch (e: Exception) {
        false
    }
}
