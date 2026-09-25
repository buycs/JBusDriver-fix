package me.jbusdriver.common

import me.jbusdriver.base.glide.GlideNoHostUrl
import me.jbusdriver.http.BUS_SITE
import me.jbusdriver.http.JAVBusService


val String.toGlideNoHostUrl: GlideNoHostUrl
    inline get() = GlideNoHostUrl(this, JAVBusService.xyzHostDomains, BUS_SITE)

/** 图片走哪个站点的防盗链 Referer。論壇图固定挂在 www.javbus.com 下, 跟站点地址切换不是一回事 */
fun String.toGlideUrlReferedBy(refererHost: String): GlideNoHostUrl =
    GlideNoHostUrl(this, JAVBusService.xyzHostDomains, refererHost)

val String.isEndWithXyzHost
    get() = JAVBusService.xyzHostDomains.any { this.endsWith(it) }