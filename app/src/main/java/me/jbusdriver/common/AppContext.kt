package me.jbusdriver.common

import android.app.Application
import android.os.Environment
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import com.umeng.analytics.MobclickAgent
import com.umeng.commonsdk.UMConfigure
import io.reactivex.plugins.RxJavaPlugins
import me.jbusdriver.BuildConfig
import me.jbusdriver.base.JBusManager
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.ui.data.AppConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap


lateinit var JBus: AppContext


class AppContext : Application() {

    val JBusServices by lazy { ConcurrentHashMap<String, JAVBusService>() }
    private val isDebug by lazy {
        // 文件标记只允许在 debug 包生效: 外部存储目录任何持有存储权限的应用都能写,
        // 否则正式版会被诱导打开详细日志
        BuildConfig.DEBUG && File(
            Environment.getExternalStorageDirectory().absolutePath + File.separator +
                    packageName
                    + File.separator + "debug"
        ).exists()
    }

    override fun onCreate() {
        super.onCreate()
        JBusManager.setContext(this)
        JBus = this

        // 主题要在第一个 Activity 起来之前定好, 否则会先闪一下浅色
        AppConfiguration.applyThemeMode(AppConfiguration.themeMode)

        //LeakCanary 2.x 由 debug 变体的 ContentProvider 自动装好,无需手工 install

        if (isDebug) {
            val formatStrategy = PrettyFormatStrategy.newBuilder()
                .showThreadInfo(true)  // (Optional) Whether to show thread info or not. Default true
                .methodCount(2)         // (Optional) How many method line to show. Default 2
                .methodOffset(0)        // (Optional) Hides internal method calls up to offset. Default 5
                // .logStrategy(customLog) // (Optional) Changes the log strategy to print out. Default LogCat
                .tag("old_driver")   // (Optional) Global tag for every log. Default PRETTY_LOGGER
                .build()

            Logger.addLogAdapter(object : AndroidLogAdapter(formatStrategy) {
                override fun isLoggable(priority: Int, tag: String?) = isDebug
            })
        }

        UMConfigure.init(this, UMConfigure.DEVICE_TYPE_PHONE, null)
        UMConfigure.setLogEnabled(isDebug)
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.AUTO)
        MobclickAgent.setCatchUncaughtExceptions(true)

        RxJavaPlugins.setErrorHandler {
            try {
                if (!isDebug) MobclickAgent.reportError(this, it)
            } catch (e: Exception) {
                //ignore  report error
            }
        }


        this.registerActivityLifecycleCallbacks(JBusManager)


    }


    override fun onLowMemory() {
        super.onLowMemory()
        JBusServices.clear()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        JBusServices.clear()
    }
}
