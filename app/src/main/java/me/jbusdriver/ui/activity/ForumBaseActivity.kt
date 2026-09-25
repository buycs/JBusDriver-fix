package me.jbusdriver.ui.activity

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.http.JAVBusService

/**
 * 論壇三页共用的一条「取 HTML -> 后台解析 -> 回主线程」链路。
 * 解析放 IO 线程: 帖子页一个楼层就上百个节点, 主线程解析会掉帧。
 */
abstract class ForumBaseActivity : BaseActivity() {

    private var request: Disposable? = null

    protected fun <T> loadPage(
        url: String,
        parse: (html: String, baseUrl: String) -> T,
        onParsed: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        request?.dispose()
        request = JAVBusService.INSTANCE.get(url)
            .subscribeOn(Schedulers.io())
            .map { parse(it, url) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { onParsed(it) },
                { e -> onError(e.message ?: "加載失敗") }
            )
            .also { rxManager.add(it) }
    }
}
