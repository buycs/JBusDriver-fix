package me.jbusdriver.http

import io.reactivex.Flowable
import me.jbusdriver.base.http.NetClient
import retrofit2.http.GET


/**
 * Created by Administrator on 2017/4/15 0015.
 *
 * 版本信息源: 仓库里随包发布的 app/src/main/assets/properties.json。
 *
 * 走 jsDelivr 而不是 raw.githubusercontent.com —— 后者实测直连超时(15s 无响应),
 * 国内用户基本拉不到, 「检查更新」会直接报失败。jsDelivr 实测 1.7s 可达。
 * 注意 jsDelivr 对分支引用有约 12h 缓存, 发版后线上值不会立刻更新, 这是可接受的。
 *
 * 不用 GitHub API: 未认证按出口 IP 限流 60 次/小时, 共享 IP 下很容易 403。
 */
interface GitHub {

    @GET("https://cdn.jsdelivr.net/gh/buycs/JBusDriver-fix@master/app/src/main/assets/properties.json")
    fun properties(): Flowable<String>

    companion object {
        val INSTANCE by lazy { NetClient.getRetrofit("https://cdn.jsdelivr.net/").create(GitHub::class.java) }
    }
}
