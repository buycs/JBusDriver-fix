package me.jbusdriver.base.ui

import android.content.Context

/**
 * 播放页的启动入口。
 *
 * 播放页（`me.jbusdriver.ui.activity.PlayerActivity`）在 app 模块，而磁力弹窗在
 * component_magnet —— 后者不能反向依赖前者（app 已经依赖它，会成环）。
 * 所以由 app 在 `Application.onCreate` 里注入实现，这里只留一个签名。
 *
 * 没注入时 [open] 静默返回：只可能发生在 Application 还没跑完的极端时序上。
 */
object PlayerLauncher {

    /**
     * 起播放页。url 支持 `magnet:` / `.m3u8` / 直链，
     * 分流判定在 `me.jbusdriver.player.ExoPlayerImpl.prepare()` 里。
     */
    var launch: ((context: Context, url: String, title: String?) -> Unit)? = null

    fun open(context: Context, url: String, title: String? = null) {
        if (url.isBlank()) return
        launch?.invoke(context, url, title)
    }
}
