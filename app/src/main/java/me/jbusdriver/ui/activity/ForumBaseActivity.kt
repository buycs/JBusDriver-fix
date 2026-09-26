package me.jbusdriver.ui.activity

import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.http.JAVBusService

/**
 * 論壇页面共用的一条「取 HTML -> 后台解析 -> 回主线程」链路。
 * 解析放 IO 线程: 帖子页一个楼层就上百个节点, 主线程解析会掉帧。
 * 放成顶层函数是因为論壇首页现在是 Fragment, 没法再继承 ForumBaseActivity。
 */
fun <T> loadForumPage(
    rxManager: CompositeDisposable,
    url: String,
    parse: (html: String, baseUrl: String) -> T,
    onParsed: (T) -> Unit,
    onError: (String) -> Unit
): Disposable = JAVBusService.INSTANCE.get(url)
    .subscribeOn(Schedulers.io())
    .map { parse(it, url) }
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { onParsed(it) },
        { e -> onError(e.message ?: "加載失敗") }
    )
    .also { rxManager.add(it) }

/** 帖子页/板块列表页仍用 Activity, 保留这层壳; 同一页重复加载时先掐掉上一次 */
abstract class ForumBaseActivity : BaseActivity() {

    private var request: Disposable? = null

    protected fun <T> loadPage(
        url: String,
        parse: (html: String, baseUrl: String) -> T,
        onParsed: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        request?.dispose()
        request = loadForumPage(rxManager, url, parse, onParsed, onError)
    }
}
