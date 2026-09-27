package me.jbusdriver.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.ScrollView

/**
 * 论坛板块页顶部热帖栏专用的 ScrollView。
 *
 * 它挂在 [com.google.android.material.appbar.AppBarLayout] 里, 而 AppBarLayout 的 Behavior 会在
 * `onInterceptTouchEvent(ACTION_MOVE)` 阶段直接把整串手势从子 View 手里抢走(拿去收顶栏),
 * 子 View 连一个 ACTION_MOVE 都收不到 —— 结果这一栏永远滚不动, 只能看到窗口内的 5 条。
 *
 * 解法是在 **ACTION_DOWN** 就把「本轮别拦我」的旗子插到上层:
 * `requestDisallowInterceptTouchEvent(true)` 会让 CoordinatorLayout 在后续 MOVE 时跳过
 * `onInterceptTouchEvent`, AppBarLayout 就没机会抢, 手势留在这一栏自己滚。
 *
 * 为什么必须写在 `onInterceptTouchEvent` 里、不能写 `setOnTouchListener`:
 * 这是一个 ViewGroup, 而条目 View 自己注册了点击(clickable), 会消费掉 ACTION_DOWN。
 * ViewGroup 的 `dispatchTouchEvent` 只在「没有任何子 View 消费」时才会回调自己的
 * onTouchListener —— 那个回调根本不会来。而 `onInterceptTouchEvent(ACTION_DOWN)` 一定会被调用。
 *
 * 副作用(有意为之): 在這一栏上按住拖动时顶栏不会收起来。这一栏本来就该自己滚,
 * 想收顶栏在下面的帖子列表里滑即可。
 */
class BoardHotScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 内容不足一屏时不必抢: 抢了会把整页滚动也一起锁死
        if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
            (canScrollVertically(1) || canScrollVertically(-1))
        ) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.onInterceptTouchEvent(ev)
    }
}
