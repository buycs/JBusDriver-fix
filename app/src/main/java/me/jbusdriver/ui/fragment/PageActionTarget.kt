package me.jbusdriver.ui.fragment

import me.jbusdriver.base.ui.fragment.TabViewPagerFragment

/**
 * 标签页三点菜单的动作落点: 由当前可见的那个列表页实现。
 * 各页只认自己那几项, 用不到的留默认空实现。
 */
interface PageActionTarget {

    /** 分页模式下的「跳页」 */
    fun showJumpPage() {}

    /** 影片列表的「全部影片」开关 */
    fun setShowAll(showAll: Boolean) {}

    /** 当前是不是「全部影片」 */
    val isShowAll: Boolean get() = false

    /** 收藏页的「修改收藏夹」 */
    fun editCollectDir() {}

    /** 当前页的条目是否已收藏; 不支持收藏的页(比如演员搜索结果)返回 null, 收藏那两项跟着收起 */
    val collected: Boolean? get() = null

    /** 收藏当前页 */
    fun addCollect() {}

    /** 取消收藏当前页 */
    fun removeCollect() {}
}

/** 当前页签下能承接动作的那个子页; 承接不了(比如类别格子页)就是 null, 三点按钮跟着收起 */
fun TabViewPagerFragment<*, *>.currentPageActionTarget(): PageActionTarget? =
    currentLeafFragment() as? PageActionTarget
