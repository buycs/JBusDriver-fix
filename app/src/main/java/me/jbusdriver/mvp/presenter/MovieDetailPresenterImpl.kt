package me.jbusdriver.mvp.presenter

import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.rxkotlin.addTo
import me.jbusdriver.base.*
import me.jbusdriver.base.common.C
import me.jbusdriver.base.mvp.model.AbstractBaseModel
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.BasePresenterImpl
import me.jbusdriver.common.isEndWithXyzHost
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.MovieDetailContract
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.MovieDetail
import me.jbusdriver.mvp.bean.checkUrl
import me.jbusdriver.mvp.bean.parseMovieDetails
import org.jsoup.Jsoup

class MovieDetailPresenterImpl(private val fromHistory: Boolean) :
    BasePresenterImpl<MovieDetailContract.MovieDetailView>(),
    MovieDetailContract.MovieDetailPresenter {


    private val loadFromNet = { s: String ->
        JAVBusService.INSTANCE.get(s).addUserCase().map { parseMovieDetails(Jsoup.parse(it)) }
            .doOnNext {
                s.urlPath.let { key -> CacheLoader.cacheDisk(key to it, C.Cache.DAY * 7) }
            }
            ?: Flowable.empty()
    }
    val model: BaseModel<String, MovieDetail> =
        object : AbstractBaseModel<String, MovieDetail>(loadFromNet) {
            override fun requestFromCache(t: String): Flowable<MovieDetail> {
                val disk = Flowable.create({ emitter: FlowableEmitter<MovieDetail> ->
                    //缓存读不出或解析失败都走 onComplete: onError 会让 concat 订阅不到后面的网络源
                    val cached = mView?.let {
                        CacheLoader.readDiskAsString(t.urlPath)?.let { text ->
                            runCatching { GSON.fromJson<MovieDetail>(text) }.getOrNull()
                        }
                    }
                    val res = if (cached != null && mView?.movie?.link?.urlHost?.isEndWithXyzHost == false) {
                        val new = cached.checkUrl(JAVBusService.defaultFastUrl)
                        if (cached != new) CacheLoader.cacheDisk(t.urlPath to new, C.Cache.DAY * 7)
                        new
                    } else cached
                    res?.let { emitter.onNext(it) } ?: emitter.onComplete()
                }, BackpressureStrategy.DROP)

                return Flowable.concat(disk, requestFor(t)).firstOrError().toFlowable()
            }
        }


    override fun onFirstLoad() {
        super.onFirstLoad()
        val fromUrl = mView?.movie?.link ?: mView?.url ?: error("need url info")
        loadDetail(fromUrl)
    }

    override fun onRefresh() {
        mView?.movie?.link?.let {
            //删除缓存和magnet缓存
            CacheLoader.acache.remove(it.urlPath)
            CacheLoader.acache.remove(it.urlPath + "_magnet")
            //重新加载
            loadDetail(it)
            //magnet 不要重新加载
        }
    }

    override fun loadDetail(url: String) {
        model.requestFromCache(url)
            .compose(SchedulersCompat.io())
            .doOnTerminate { mView?.dismissLoading() }
            .subscribeWith(object : SimpleSubscriber<MovieDetail>() {
                override fun onStart() {
                    super.onStart()
                    mView?.showLoading()
                }

                override fun onNext(t: MovieDetail) {
                    super.onNext(t)
                    //movie
                    mView?.showContent(t.generateMovie(url))
                    //detail
                    mView?.showContent(t)
                }
            })
            .addTo(rxManager)

    }

    fun MovieDetail.generateMovie(url: String): Movie {
        val code = headers.first().value.trim()
        return Movie(
            title.replace(code, "", true).trim(),
            this.cover.replace("/cover", "/thumb").replace("_b", ""),
            code,
            headers.component2().value,
            url
        )
    }


    override fun restoreFromState() {
        super.restoreFromState()
        mView?.movie?.link?.let {
            loadDetail(it)
        }
    }

}