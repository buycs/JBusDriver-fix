package me.jbusdriver.mvp.presenter

import com.google.gson.JsonObject
import io.reactivex.Flowable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.base.*
import me.jbusdriver.base.common.C
import me.jbusdriver.base.mvp.presenter.BasePresenterImpl
import me.jbusdriver.http.GitHub
import me.jbusdriver.mvp.MainContract
import me.jbusdriver.mvp.bean.NoticeBean
import me.jbusdriver.mvp.bean.UpdateBean


class MainPresenterImpl : BasePresenterImpl<MainContract.MainView>(), MainContract.MainPresenter {
    override fun onFirstLoad() {
        super.onFirstLoad()
        fetchUpdate()
    }

    private fun fetchUpdate() {
        Flowable.concat<JsonObject>(
            CacheLoader.justLru(C.Cache.ANNOUNCE_VALUE).flatMap { announce(it) },
            CacheLoader.justDisk(C.Cache.ANNOUNCE_VALUE).flatMap { announce(it) },
            GitHub.INSTANCE.announce().addUserCase()
                .map { GSON.fromJson<JsonObject>(it) }
                .doOnNext { CacheLoader.cacheDisk(C.Cache.ANNOUNCE_VALUE to it, C.Cache.DAY / 4) }
        )
            .firstOrError()
            .map {
                Pair(
                    GSON.fromJson(it.get("update"), UpdateBean::class.java),
                    GSON.fromJson(it.get("notice"), NoticeBean::class.java)
                )
            }
            .retry(1)
            .toFlowable()
            .compose(SchedulersCompat.io<Pair<UpdateBean, NoticeBean?>>())
            .subscribeBy(onNext = {
                mView?.showContent(it.first)
                mView?.showContent(it.second)
            }, onError = {
                KLog.w("fetchUpdate error ${it.message}")
            })
            .addTo(rxManager)
    }

    /**
     * 缓存里的公告解析不出来(旧格式/写坏)时必须发 onComplete 而不是 onError,
     * 否则 concat 会直接终止, 永远走不到后面的网络源。
     */
    private fun announce(text: String): Flowable<JsonObject> =
        runCatching { GSON.fromJson<JsonObject>(text) }.getOrNull()
            ?.takeIf { it.size() > 0 }
            ?.let { Flowable.just(it) } ?: Flowable.empty<JsonObject>()

}