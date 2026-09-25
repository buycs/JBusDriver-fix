package me.jbusdriver.http

import io.reactivex.Flowable
import me.jbusdriver.base.KLog
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.common.JBus
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Created by Administrator on 2017/4/8.
 */

/** 站点地址写死在本地: 不再从云端 announce 取备用域名, 也不做探活换站 */
const val BUS_SITE = "https://www.javbus.com"

interface JAVBusService {


    //https://announce.javbus8.com/website.php
    @GET
    fun get(@Url url: String, @Header("existmag") existmag: String = ""): Flowable<String>



    companion object {
        /**
         * 欧美站用过的顶级域。现在只剩识别作用: 收藏夹/历史记录里存量的老链接要靠它判断
         * 是不是站外链接, 图片缓存键也据此决定用整串 URL 还是只用路径。
         */
        val xyzHostDomains: Set<String> = setOf(".one", ".hair", ".zone", ".red", ".xyz")

        val INSTANCE: JAVBusService
            get() = getInstance(BUS_SITE)

        fun getInstance(source: String): JAVBusService {
            return JBus.JBusServices.getOrPut(source) {
                createService(source)
            }.apply {
                KLog.d("instances : ${JBus.JBusServices}, service for : $source")
            }
        }

        private fun createService(url: String) =
            NetClient.getRetrofit("${url.trimEnd('/')}/").create(JAVBusService::class.java)

    }
}