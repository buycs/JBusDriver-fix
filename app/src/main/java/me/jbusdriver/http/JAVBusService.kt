package me.jbusdriver.http

import io.reactivex.Flowable
import me.jbusdriver.base.KLog
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.common.JBus
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Created by Administrator on 2017/4/8.
 */
interface JAVBusService {


    //https://announce.javbus8.com/website.php
    @GET
    fun get(@Url url: String, @Header("existmag") existmag: String = ""): Flowable<String>



    companion object {
        @Volatile
        var defaultFastUrl = "https://www.seedmm.life"

        @Volatile
        var defaultXyzUrl = "https://www.javbus.one"

        // 站点探测在 IO 线程写入, Glide 在主线程读取; 写一次读多次, 用写时复制集合最省事
        val xyzHostDomains: MutableSet<String> by lazy {
            CopyOnWriteArraySet(listOf(topLevelDomain(defaultXyzUrl)))
        }

        /** 始终跟随当前 defaultFastUrl, 不再有可变的全局单例 */
        val INSTANCE: JAVBusService
            get() = getInstance(defaultFastUrl)

        fun getInstance(source: String): JAVBusService {
            return JBus.JBusServices.getOrPut(source) {
                createService(source)
            }.apply {
                KLog.d("instances : ${JBus.JBusServices}, defaultFastUrl : $defaultFastUrl")
            }
        }

        private fun topLevelDomain(url: String): String {
            val host = runCatching { okhttp3.HttpUrl.parse(url)?.host() }.getOrNull()
                ?: url.substringAfter("://", url).substringBefore("/")
            return "." + host.substringAfterLast(".")
        }

        private fun createService(url: String) =
            NetClient.getRetrofit("${url.trimEnd('/')}/").create(JAVBusService::class.java)

    }
}