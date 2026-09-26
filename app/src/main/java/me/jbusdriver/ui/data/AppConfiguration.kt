package me.jbusdriver.ui.data

import androidx.appcompat.app.AppCompatDelegate
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.mvp.bean.GridColumnChangeEvent
import me.jbusdriver.mvp.bean.HideRecentChangeEvent
import me.jbusdriver.mvp.bean.PageChangeEvent
import me.jbusdriver.mvp.bean.UiModeChangeEvent
import kotlin.properties.Delegates

/**
 * Created by Administrator on 2017/9/9.
 */


object AppConfiguration {


    //region pageMode value
    object PageMode {
        const val Page = 1
        const val Normal = 0
    }

    private const val PageModeS: String = "PageMode"
    var pageMode: Int by Delegates.vetoable(
        getSp(PageModeS)?.toIntOrNull() ?: let {
            saveSp(PageModeS, "1")
            1
        }) { _, old, new ->
        return@vetoable (new in 0..1 && old != new).also {
            if (it) {
                saveSp(PageModeS, new.toString())
                RxBus.post(PageChangeEvent(new))
            }
        }
    }

    //endregion


    //region uiMode 外壳样式: 侧边抽屉 / 底部导航
    object UiMode {
        const val Drawer = 0
        const val Bottom = 1
    }

    private const val UiModeS: String = "UiMode"
    var uiMode: Int by Delegates.vetoable(
        getSp(UiModeS)?.toIntOrNull() ?: let {
            saveSp(UiModeS, "0")
            0
        }) { _, old, new ->
        return@vetoable (new in 0..1 && old != new).also {
            if (it) {
                saveSp(UiModeS, new.toString())
                RxBus.post(UiModeChangeEvent(new))
            }
        }
    }

    //endregion


    //region theme 跟随系统 / 浅色 / 深色
    object ThemeMode {
        const val System = 0
        const val Light = 1
        const val Dark = 2
    }

    private const val ThemeModeS: String = "ThemeMode"
    var themeMode: Int by Delegates.vetoable(
        getSp(ThemeModeS)?.toIntOrNull() ?: let {
            saveSp(ThemeModeS, "0")
            0
        }) { _, old, new ->
        return@vetoable (new in 0..2 && old != new).also {
            if (it) {
                saveSp(ThemeModeS, new.toString())
                applyThemeMode(new)
            }
        }
    }

    /**
     * 只切 night mode, 不自己 recreate: AppCompatDelegate 会把在跑的 Activity 逐个重建。
     * 前提是主题走 DayNight 父类, 颜色全部从 values-night 覆盖。
     */
    fun applyThemeMode(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    //endregion


    //region gridColumn 影片列表一行放几个, 1 就是原来的按行显示
    object GridColumn {
        const val LINE = 1
        const val MIN = 1
        const val MAX = 4
    }

    private const val GridColumnS: String = "GridColumn"
    var gridColumn: Int by Delegates.vetoable(
        getSp(GridColumnS)?.toIntOrNull() ?: let {
            saveSp(GridColumnS, GridColumn.LINE.toString())
            GridColumn.LINE
        }) { _, old, new ->
        return@vetoable (new in GridColumn.MIN..GridColumn.MAX && old != new).also {
            if (it) {
                saveSp(GridColumnS, new.toString())
                RxBus.post(GridColumnChangeEvent(new))
            }
        }
    }

    //endregion


    //region homePage 底部样式落地的那个页签, 存的是 bottom_* 那套 id
    private const val HomePageS: String = "HomePage"
    private val defaultHomePageId = R.id.bottom_movie
    var homePageId: Int by Delegates.vetoable(
        getSp(HomePageS)?.toIntOrNull() ?: defaultHomePageId
    ) { _, old, new ->
        (new != 0 && old != new).also {
            if (it) saveSp(HomePageS, new.toString())
        }
    }

    //endregion


    //region 最近任务隐藏
    private const val HideRecentS: String = "HideRecent"
    var hideRecent: Boolean by Delegates.observable(
        java.lang.Boolean.parseBoolean(getSp(HideRecentS))
    ) { _, old, new ->
        if (old != new) {
            saveSp(HideRecentS, new.toString())
            RxBus.post(HideRecentChangeEvent(new))
        }
    }

    //endregion


    private const val HistoryS: String = "HistoryS"
    var enableHistory: Boolean = true


}
