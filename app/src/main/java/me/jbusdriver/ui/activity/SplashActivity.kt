package me.jbusdriver.ui.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.util.ArrayMap
import com.google.gson.JsonObject
import com.tbruyelle.rxpermissions2.RxPermissions
import com.umeng.analytics.MobclickAgent
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.http.GitHub
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.ui.data.enums.DataSourceType
import org.jsoup.Jsoup

class SplashActivity : BaseActivity() {

    private var urls = arrayMapof<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        immersionBar.transparentBar().init()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        init()
    }

    private fun init() {
        RxPermissions(this).request(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            .flatMap {
                initUrls()
            }
            .doOnError {
                KLog.e("获取可用url错误 :$it")
                MobclickAgent.reportError(viewContext, it)
                CacheLoader.acache.remove(C.Cache.BUS_URLS)
            }
            .retry(1)
            .doFinally {
                postMain {
                    toast("load url : ${JAVBusService.defaultFastUrl}")
                    MainActivity.start(this)
                    finish()
                }.addTo(rxManager)
            }
            .subscribeBy(onNext = {
                KLog.w("init urls ok : $it")

                it.get(DataSourceType.CENSORED.key)?.let {
                    JAVBusService.defaultFastUrl = it.urlHost
                }
            }, onError = {
                it.printStackTrace()
                KLog.w("init urls error : $it")
            }, onComplete = {
            })
            .addTo(rxManager)

    }

    private fun initUrls(): Observable<ArrayMap<String, String>> {
        //内存里已有站点地址时直接用; 解析不出来(旧格式)就清掉, 继续走磁盘/网络
        CacheLoader.lru.get(C.Cache.BUS_URLS)?.takeUnless { it.isNullOrBlank() }?.let { text ->
            val parsed = runCatching { GSON.fromJson<ArrayMap<String, String>>(text) }.getOrNull()
            if (parsed != null) return Observable.just(parsed)
            CacheLoader.lru.remove(C.Cache.BUS_URLS)
        }
        //内存没有地址时, 先从disk获取缓存的, 没有则从网络下载
        val urlsFromDisk = CacheLoader.justDiskMap(C.Cache.BUS_URLS)
            .filter { it.isNotEmpty() } //空表当没有, 交给后面的网络流程
            .map { arrayMapof<String, String>().apply { putAll(it) } }
            //磁盘缓存坏了只能忽略: 这里 onError 会让 concat 永远订阅不到后面的网络源
            .onErrorResumeNext { t: Throwable ->
                KLog.e("站点地址缓存不可用, 改为从网络获取: $t")
                CacheLoader.acache.remove(C.Cache.BUS_URLS)
                Flowable.empty<ArrayMap<String, String>>()
            }
        val urlsFromUpdateCache =
            Flowable.concat(
                CacheLoader.justLru(C.Cache.ANNOUNCE_VALUE),
                GitHub.INSTANCE.announce().addUserCase()
                    .doOnError { KLog.e("announce error", it) }
                    .doOnNext {
                        KLog.i("announce ok", it)
                        CacheLoader.cacheDisk(C.Cache.ANNOUNCE_VALUE to it, C.Cache.DAY / 4)
                    }
            )
                .firstOrError().toFlowable()
                .map { source ->
                    val r = GSON.fromJson<JsonObject>(source) ?: JsonObject()
                    arrayMapof<String, String>().apply {
                        val xyzLoader = r.getAsJsonObject("xyzLoader") ?: JsonObject()
                        JAVBusService.defaultXyzUrl =
                            xyzLoader.get("url")?.asString?.removeSuffix("/").orEmpty()
                        JAVBusService.xyzHostDomains.addAll(
                            xyzLoader.getAsJsonArray("legacyHost")?.map { it.asString }
                                ?: emptyList())
                        val availableUrls = r.get("backUp")?.asJsonArray
                        //赋值一个默认的(随机)
                        availableUrls?.let {
                            it.mapNotNull { it.asString }.shuffled().firstOrNull()?.let {
                                JAVBusService.defaultFastUrl = it
                                urls[DataSourceType.CENSORED.key] = it
                            }
                        }
                        put(DataSourceType.CENSORED.key, availableUrls.toString())
                        KLog.d("init urls first :$source for $this")
                    }
                }
                .flatMap {
                    urls = it
                    //backUp 缺失时上面写入的是字符串 "null", 这里解析不出来只能是空列表
                    val candidates = runCatching {
                        GSON.fromJson<List<String>>(it[DataSourceType.CENSORED.key].orEmpty())
                    }.getOrNull().orEmpty()
                    val mapFlow = candidates.map {
                        Flowable.combineLatest(Flowable.just<String>(it),
                            JAVBusService.INSTANCE.get(it).addUserCase(15).onErrorReturnItem(""),
                            BiFunction<String, String?, Pair<String, String>> { t1, t2 -> t1 to t2 })
                    }
                    Flowable.mergeDelayError(mapFlow).filter { it.second.isNotBlank() }.take(1)
                }
                .firstOrError()
                .doOnError { CacheLoader.acache.remove(C.Cache.ANNOUNCE_VALUE) }
                .map {
                    val ds = DataSourceType.values().takeLast(DataSourceType.values().size - 1)
                        .toMutableList()
                    Jsoup.parse(it.second).select(".navbar-nav a").forEach { box ->
                        ds.find { box.text() == it.key }?.let {
                            ds.remove(it)

                            urls.put(it.key, box.attr("href").removeSuffix("/"))
                        }
                    }
                    urls[DataSourceType.XYZ.key]?.let {
                        //欧美
                        urls[DataSourceType.XYZ_ACTRESSES.key] =
                            "$it/${DataSourceType.XYZ_ACTRESSES.key.split("/").last()}"
                        urls.put(
                            DataSourceType.XYZ_GENRE.key,
                            "$it/${DataSourceType.XYZ_GENRE.key.split("/").last()}"
                        )
                    }
                    urls[DataSourceType.CENSORED.key] = it.first
                    // 探活成功的站点要同步给图片加载: 所有封面图的 Referer/域名都跟 defaultFastUrl
                    JAVBusService.defaultFastUrl = it.first

                    //change xyz
                    if (JAVBusService.defaultXyzUrl.isNotBlank()) {
                        urls[DataSourceType.XYZ.key] = JAVBusService.defaultXyzUrl
                        urls[DataSourceType.XYZ_ACTRESSES.key] =
                            "${JAVBusService.defaultXyzUrl}/actresses"
                        urls[DataSourceType.XYZ_GENRE.key] =
                            "${JAVBusService.defaultXyzUrl}/genre"
                    } else {
                        //xyzHostDomains 里是 ".one" 这类带点的顶级域, 地址后缀也要补上点才能整串替换
                        val host = JAVBusService.xyzHostDomains.firstOrNull() ?: ".work"
                        val xyz = urls[DataSourceType.XYZ.key]
                        val baseUrlSuffix = xyz?.substringAfterLast(".")?.let { ".$it" }
                        if (xyz != null && baseUrlSuffix != null) {
                            urls[DataSourceType.XYZ.key] = xyz.replace(baseUrlSuffix, host)
                            urls[DataSourceType.XYZ_ACTRESSES.key]?.let {
                                urls[DataSourceType.XYZ_ACTRESSES.key] = it.replace(baseUrlSuffix, host)
                            }
                            urls[DataSourceType.XYZ_GENRE.key]?.let {
                                urls[DataSourceType.XYZ_GENRE.key] = it.replace(baseUrlSuffix, host)
                            }
                        }
                    }

                    CacheLoader.cacheLruAndDisk(
                        C.Cache.BUS_URLS to urls
                    ) //缓存所有的urls
                    CacheLoader.lru.put(
                        DataSourceType.CENSORED.key + "false",
                        it.second
                    ) //默认有种的
                    KLog.d("init urls second :$urls ")
                    urls
                }.toFlowable()
        return Flowable.concat<ArrayMap<String, String>>(urlsFromDisk, urlsFromUpdateCache)
            .firstElement().toObservable()
            .subscribeOn(Schedulers.io())
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, SplashActivity::class.java))
        }
    }
}
