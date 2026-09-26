package me.jbusdriver.mvp.bean

/**
 * Created by Administrator on 2017/7/29.
 */

data class SearchWord(val query: String)

@Deprecated("not use ")
data class CollectErrorEvent(val key: String, val msg: String)


//config
data class PageChangeEvent(val mode: Int)

data class UiModeChangeEvent(val mode: Int)

/** 影片列表网格列数变了, 已显示的列表要就地换 spanCount */
data class GridColumnChangeEvent(val columns: Int)

data class HideRecentChangeEvent(val hide: Boolean)

data class BackUpEvent(var path: String, var total: Int, var index: Int)
