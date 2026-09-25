package me.jbusdriver.mvp.presenter

import io.reactivex.Flowable
import me.jbusdriver.base.*
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.model.AbstractBaseModel
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.PageLink
import me.jbusdriver.mvp.bean.loadMovieFromDoc
import me.jbusdriver.mvp.bean.newPageMovie
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 首页列表
 */
open class HomeMovieListPresenterImpl(val type: DataSourceType, val link: ILink) : LinkAbsPresenterImpl<Movie>(link) {

    private val saveKey: String
        inline get() = "${type.key}$IsAll"
    private val service by lazy { JAVBusService.getInstance(type.url) }

    private val loadFromNet = { page: Int ->
        val urlN = if (page == 1) type.url else "${type.url}${type.prefix}$page"
        KLog.d("loadFromNet $urlN")
        //existmag=all
        //add his
        val pageLink = PageLink(page = page, title = type.key, link = urlN)
        addHistory(pageLink)
        service.get(urlN, if (IsAll) "all" else "").addUserCase().doOnNext {
            if (page == 1 && !it.isNullOrBlank()) CacheLoader.lru.put(saveKey, it!!)
        }.map { Jsoup.parse(it) }
    }


    override val model: BaseModel<Int, Document> = object : AbstractBaseModel<Int, Document>(loadFromNet) {
        override fun requestFromCache(t: Int): Flowable<Document> =
            Flowable.concat(
                CacheLoader.justLru(saveKey).map { Jsoup.parse(it) },
                requestFor(t)
            ).firstOrError().toFlowable()
    }


    override fun stringMap(page: PageInfo, str: Document) = loadMovieFromDoc(str).let {
        when (mView?.pageMode) {
            AppConfiguration.PageMode.Page -> {
                listOf(newPageMovie(page.activePage, page.referPages)) + it
            }
            else -> it
        }
    }


    override fun onRefresh() {
        CacheLoader.removeCacheLike(saveKey, isRegex = false)
        super.onRefresh()
    }

}