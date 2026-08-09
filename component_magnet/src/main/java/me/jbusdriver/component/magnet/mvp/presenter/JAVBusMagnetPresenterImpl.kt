package me.jbusdriver.component.magnet.mvp.presenter

import io.reactivex.Flowable
import io.reactivex.rxkotlin.addTo
import me.jbusdriver.base.*
import me.jbusdriver.base.http.NetClient
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.bean.ResultPageBean
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.AbstractRefreshLoadMorePresenterImpl
import me.jbusdriver.component.magnet.http.JAVBusMagnetService
import me.jbusdriver.component.magnet.mvp.MagnetListContract
import me.jbusdriver.component.magnet.mvp.bean.Magnet
import org.jsoup.nodes.Document

/**
 * javbus 官网磁力加载器
 * 通过详情页脚本中的 gid/uc/img 拼接 ajax 接口获取磁力
 */
class JAVBusMagnetPresenterImpl(private val detailUrl: String) :
    AbstractRefreshLoadMorePresenterImpl<MagnetListContract.MagnetListView, Magnet>(),
    MagnetListContract.MagnetListPresenter {

    private val service: JAVBusMagnetService by lazy {
        NetClient.getRetrofit("${detailUrl.urlHost}/").create(JAVBusMagnetService::class.java)
    }

    override val model: BaseModel<Int, Document>
        get() = error("not call model")

    override fun stringMap(pageInfo: PageInfo, str: Document): List<Magnet> = error("not call stringMap")


    override fun loadData4Page(page: Int) {
        val curPage = PageInfo(page, page + 1)
        val cacheKey = "javbus_official_${detailUrl}_${curPage.activePage}"
        val cache = Flowable.concat(CacheLoader.justLru(cacheKey), CacheLoader.justDisk(cacheKey)).firstElement()
            .map { GSON.fromJson<List<Magnet>>(it) }.toFlowable()
        val loaderFormNet = Flowable.fromCallable {
            val html = service.get(detailUrl).blockingFirst()
            val gid = Regex("gid\\s*=\\s*(\\d+)").find(html)?.groupValues?.getOrNull(1).orEmpty()
            val uc = Regex("uc\\s*=\\s*(\\d+)").find(html)?.groupValues?.getOrNull(1).orEmpty()
            val img = Regex("img\\s*=\\s*'([^']+)'").find(html)?.groupValues?.getOrNull(1).orEmpty()
            if (gid.isEmpty()) {
                emptyList()
            } else {
                val ajaxUrl = "${detailUrl.urlHost}/ajax/uncledatoolsbyajax.php?gid=$gid&lang=zh&img=$img&uc=$uc&floor=1"
                val magnetHtml = service.get(ajaxUrl, referer = detailUrl).blockingFirst()
                val magnets = org.jsoup.Jsoup.parse("<table>$magnetHtml</table>").select("tr").mapNotNull { tr ->
                    val cells = tr.select("td")
                    if (cells.size < 3) return@mapNotNull null
                    val link = cells[0].select("a[href*=magnet:]").attr("href").orEmpty()
                    if (link.isEmpty()) return@mapNotNull null
                    Magnet(
                        cells[0].select("a[href*=magnet:]").text(),
                        cells[1].text(),
                        cells[2].text(),
                        link
                    )
                }
                magnets
            }
        }.doOnNext {
            if (it.isNotEmpty() && page <= 1) {
                CacheLoader.cacheDisk(cacheKey to it)
                CacheLoader.cacheLru(cacheKey to it)
            }
        }.onErrorReturn { emptyList() }
        Flowable.concat(cache, loaderFormNet).firstOrError().toFlowable()
            .map { ResultPageBean(curPage, it) }
            .compose(SchedulersCompat.io())
            .subscribeWith(ListDefaultSubscriber(curPage))
            .addTo(rxManager)
    }

    override fun lazyLoad() {
        onFirstLoad()
    }

    override fun hasLoadNext(): Boolean = false

    override fun fetchMagLink(url: String): String = url
}