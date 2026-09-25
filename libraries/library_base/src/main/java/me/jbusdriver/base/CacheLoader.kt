package me.jbusdriver.base

import android.app.Activity
import android.app.ActivityManager
import android.support.v4.util.LruCache
import com.google.gson.stream.JsonReader
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.base.JBusManager.context
import java.io.StringReader
import java.util.concurrent.TimeUnit


object CacheLoader {
    private const val TAG = "CacheLoader"


    private fun initMemCache(): LruCache<String, String> {
        val memoryInfo = ActivityManager.MemoryInfo()
        val myActivityManager = JBusManager.context.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        //获得系统可用内存，保存在MemoryInfo对象上
        myActivityManager.getMemoryInfo(memoryInfo)
        val memSize = memoryInfo.availMem.formatFileSize()
        KLog.t(TAG).d("max availMem = $memSize")
        if (memoryInfo.lowMemory) {
            KLog.w("可能的内存不足")
            toast("当前可用内存:$memSize,请注意释放内存")
        }
        val cacheSize = if (memoryInfo.availMem > 32 * 1024 * 1024) 4 * 1024 * 1024 else 2 * 1024 * 1024
        KLog.t(TAG).d("max cacheSize = ${cacheSize.toLong().formatFileSize()}")
        return object : LruCache<String, String>(cacheSize) { //4m
            override fun entryRemoved(evicted: Boolean, key: String, oldValue: String, newValue: String?) {
                KLog.i(
                    String.format(
                        "entryRemoved : evicted = %s , key = %20s , oldValue = %30s , newValue = %30s",
                        evicted.toString(),
                        key,
                        oldValue,
                        newValue
                    )
                )
                if (evicted) oldValue.let { null } ?: oldValue.let { newValue }
            }

            override fun sizeOf(key: String, value: String): Int {
                val length = value.toByteArray().size
                KLog.i("key = $key  sizeOf = [$length]bytes format:${(this.size() + length).toLong().formatFileSize()}")
                return length
            }

        }
    }

    @JvmStatic
    val lru: LruCache<String, String> by lazy {
        initMemCache()
    }


    @JvmStatic
    val acache: ACache  by lazy {
        ACache.get(context)
    }

    /*============================cache====================================*/
    // 统一约定: 写入走 v2Str(String 原样存, 其它对象存成 JSON), 读取方拿到的一定是这一份文本
    fun cacheLruAndDisk(pair: Pair<String, Any>, seconds: Int? = null) {
        cacheLru(pair)
        cacheDisk(pair, seconds)
    }

    fun cacheLru(pair: Pair<String, Any>) = lru.put(pair.first, v2Str(pair.second))
    fun cacheDisk(pair: Pair<String, Any>, seconds: Int? = null) =
        seconds?.let { acache.put(pair.first, v2Str(pair.second), seconds) }
            ?: acache.put(pair.first, v2Str(pair.second))

    private fun v2Str(obj: Any): String = when (obj) {
        is CharSequence -> obj.toString()
        else -> obj.toJsonString()
    }

    /**
     * 兼容旧版本 cacheLruAndDisk 多编码一层的磁盘缓存(值被存成 `"..."` 形式的 JSON 字符串),
     * 直接 GSON.fromJson 会抛 JsonSyntaxException 并把整条流打成 onError。
     * 只对磁盘缓存生效: 内存缓存随进程重启即为新格式。
     */
    private fun unwrapLegacy(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (!trimmed.startsWith("\"")) return raw
        return runCatching {
            JsonReader(StringReader(trimmed)).use { it.nextString() }
        }.getOrDefault(raw)
    }

    fun readDiskAsString(key: String): String? = unwrapLegacy(acache.getAsString(key))

    /** 兼容旧格式的 map 型磁盘缓存(如站点 url 列表) */
    fun readCacheMap(key: String): Map<String, String>? =
        readDiskAsString(key)?.let { runCatching { GSON.fromJson<Map<String, String>>(it) } }.getOrNull()

    /** 只做磁盘读取的 Flowable, 解析失败时发 onError 而不是给个空 map 骗过 concat */
    fun justDiskMap(key: String): Flowable<Map<String, String>> = Flowable.defer {
        val text = readDiskAsString(key) ?: return@defer Flowable.empty<Map<String, String>>()
        Flowable.fromCallable {
            GSON.fromJson<Map<String, String>>(text) ?: error("empty cache for $key")
        }
    }

    /*============================cache to flowable====================================*/
    fun fromLruAsync(key: String): Flowable<String> =
        Flowable.interval(0, 800, TimeUnit.MILLISECONDS, Schedulers.io()).flatMap {
            val v = lru[key]
            v?.let { Flowable.just(it) } ?: Flowable.empty()
        }.timeout(6, TimeUnit.SECONDS, Flowable.empty()).take(1).subscribeOn(Schedulers.io())

    fun fromDiskAsync(key: String, add2Lru: Boolean = true): Flowable<String> =
        Flowable.interval(0, 800, TimeUnit.MILLISECONDS, Schedulers.io()).flatMap {
            val v = acache.getAsString(key)
            v?.let { Flowable.just(it) } ?: Flowable.empty()
        }.timeout(6, TimeUnit.SECONDS, Flowable.empty()).take(1).doOnNext { if (add2Lru) lru.put(key, it) }.subscribeOn(
            Schedulers.io()
        )


    fun justLru(key: String): Flowable<String> {
        val v = lru[key]
        return v?.let { Flowable.just(v) } ?: Flowable.empty()
    }

    /** defer: 磁盘读取推迟到订阅线程, 不再在组装链路的线程(通常是主线程)上做 IO */
    fun justDisk(key: String, add2Lru: Boolean = true): Flowable<String> = Flowable.defer {
        val v = readDiskAsString(key) ?: return@defer Flowable.empty<String>()
        Flowable.just(v).doOnNext { if (add2Lru) lru.put(key, v) }
    }

    /*===============================remove cache=====================================*/
    /**
     * 只会先从lru中删除再删除disk的
     */
    fun removeCacheLike(vararg keys: String, isRegex: Boolean = false) {
        Schedulers.computation().createWorker().schedule {
            lru.snapshot().keys.let { cacheCopyKeys ->
                keys.forEach { removeKey ->
                    val filterAction: (String) -> Boolean =
                        { s -> if (isRegex) s.contains(removeKey.toRegex()) else s.contains(removeKey) }
                    cacheCopyKeys.filter(filterAction).forEach {
                        KLog.i("removeCacheLike : $it")
                        cacheCopyKeys.remove(it); lru.remove(it);acache.remove(it)
                    }
                }
            }
        }
    }

}
