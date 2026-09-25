package me.jbusdriver.component.magnet

import me.jbusdriver.component.magnet.loaders.MagnetLoaders
import org.json.JSONArray
import org.json.JSONObject

/**
 * loader 编译进主 APK,这里只做进程内调用与异常兜底(原先是跨进程 Phantom 服务代理)。
 * getMagnets 保留 JSONArray 字符串出参:调用方按 List<Magnet> 解析,不必再跨进程。
 */
object MagnetLoaderApi {

    fun getMagnets(loader: String, key: String, page: Int) = kotlin.runCatching {
        JSONArray(MagnetLoaders.Loaders[loader]?.loadMagnets(key, page) ?: emptyList<JSONObject>()).toString()
    }.getOrDefault("")

    fun fetchMagLink(loader: String, url: String) = kotlin.runCatching {
        MagnetLoaders.Loaders[loader]?.fetchMagnetLink(url) ?: ""
    }.getOrDefault("")

    fun hasNext(loader: String) = kotlin.runCatching {
        MagnetLoaders.Loaders[loader]?.hasNexPage ?: false
    }.getOrDefault(false)
}
