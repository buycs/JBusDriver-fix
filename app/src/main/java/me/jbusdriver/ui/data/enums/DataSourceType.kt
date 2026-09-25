package me.jbusdriver.ui.data.enums

import me.jbusdriver.http.BUS_SITE

/**
 * Created by Administrator on 2017/4/14 0014.
 */
enum class DataSourceType(val key: String, val prefix: String = "/", val url: String = BUS_SITE) {
    CENSORED("有碼", "/page/"), //有码

    GENRE("有碼類別", url = "$BUS_SITE/genre"), //类别
    ACTRESSES("有碼女優", url = "$BUS_SITE/actresses"), //女优

    UNCENSORED("無碼", "/page/", "$BUS_SITE/uncensored"), //无码
    UNCENSORED_GENRE("無碼類別", url = "$BUS_SITE/uncensored/genre"), //无码类别
    UNCENSORED_ACTRESSES("無碼女優", url = "$BUS_SITE/uncensored/actresses"), //无码女优

    GENRE_HD("高清", url = "$BUS_SITE/genre/hd"), //高清
    Sub("字幕", url = "$BUS_SITE/genre/sub");//字幕
}
