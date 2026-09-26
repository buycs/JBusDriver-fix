package me.jbusdriver.ui.data

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import me.jbusdriver.R

/**
 * 底部导航样式的六个页签。id 单独定义在 values/ids.xml,
 * 因为抽屉那套用的是 menu item id, 两套要能同时存在。
 */
data class BottomTab(@IdRes val id: Int, val label: String, @DrawableRes val iconRes: Int)

object BottomTabs {

    val MOVIE = BottomTab(R.id.bottom_movie, "影片", R.drawable.ic_movie_ma_24dp)
    val ACTRESS = BottomTab(R.id.bottom_actress, "女优", R.drawable.ic_insert_emoticon_black_24dp)
    val SEARCH = BottomTab(R.id.bottom_search, "搜索", R.drawable.ic_search_black_24dp)
    val COLLECT = BottomTab(R.id.bottom_collect, "收藏", R.drawable.ic_collections_bookmark_24dp)
    val FORUM = BottomTab(R.id.bottom_forum, "论坛", R.drawable.ic_bubble_chart_black_24dp)
    val SETTING = BottomTab(R.id.bottom_setting, "设置", R.drawable.ic_settings_black_24dp)

    val ALL by lazy { listOf(MOVIE, ACTRESS, SEARCH, COLLECT, FORUM, SETTING) }

    /** 首页设置里只能选内容页, 目前开放影片和搜索两项 */
    val SELECTABLE by lazy { listOf(MOVIE, SEARCH) }

    fun byId(@IdRes id: Int): BottomTab? = ALL.firstOrNull { it.id == id }
}
