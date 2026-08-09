package me.jbusdriver.component.magnet.http

import io.reactivex.Flowable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * 直接请求 javbus 官网的磁力 ajax 接口
 */
interface JAVBusMagnetService {

    @GET
    fun get(
        @Url url: String,
        @Header("existmag") existmag: String = "",
        @Header("Referer") referer: String = "",
        @Header("X-Requested-With") xRequestedWith: String = "XMLHttpRequest"
    ): Flowable<String>
}