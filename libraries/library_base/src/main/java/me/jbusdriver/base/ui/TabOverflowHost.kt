package me.jbusdriver.base.ui

/**
 * 页面动作菜单的宿主, 由承载页签的 Activity 实现。
 *
 * 底部样式下顶栏不再显示页面级动作, 那些动作改由标签页最右侧的三点菜单弹出;
 * 页面据此决定要不要摆那个三点按钮 —— 抽屉样式的顶栏已经带着同一批动作, 再摆一份就是重复。
 */
interface TabOverflowHost {

    /** 当前外壳是不是底部样式 */
    val isBottomShell: Boolean
}
