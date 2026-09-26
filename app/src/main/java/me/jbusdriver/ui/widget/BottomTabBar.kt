package me.jbusdriver.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import me.jbusdriver.R
import me.jbusdriver.ui.data.BottomTab

/**
 * 底部页签栏。
 *
 * 没用 Material 的 BottomNavigationView: 它的页签上限 5 个写死在 NavigationBarMenu 里,
 * material 1.12 也没有公开调高上限的入口(只有 getMaxItemCount, 没有 setter), 而样式要求六个页签。
 * 这里按同样的外观自己画: 每项一个图标加文字, 单选, 选中走 colorPrimary。
 */
class BottomTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private class Item(val root: View, val icon: ImageView, val label: AppCompatTextView)

    private val items = LinkedHashMap<Int, Item>()
    private var selectedId = 0

    private val checkedColor by lazy { ContextCompat.getColor(context, R.color.colorPrimary) }
    private val normalColor by lazy { ContextCompat.getColor(context, R.color.secondText) }

    /** 页签被点, 参数是页签 id */
    var onTabSelected: ((Int) -> Unit)? = null

    val selectedTabId: Int get() = selectedId

    init {
        orientation = HORIZONTAL
        setBackgroundColor(themeBackgroundColor())
    }

    /**
     * 页签栏底色跟随主题。
     * 这里不能写死 R.color.white: 深色档下会在屏幕底部留一条白边。
     */
    private fun themeBackgroundColor(): Int {
        val value = TypedValue()
        val resolved = context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)
        return when {
            !resolved -> ContextCompat.getColor(context, R.color.white)
            value.resourceId != 0 -> ContextCompat.getColor(context, value.resourceId)
            else -> value.data
        }
    }

    fun setTabs(tabs: List<BottomTab>) {
        removeAllViews()
        items.clear()
        tabs.forEach { tab ->
            val item = createItem(tab)
            addView(item.root, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    fun hasTab(@IdRes id: Int) = items.containsKey(id)

    /** 只刷新高亮, 不回调 [onTabSelected], 恢复现场时用 */
    fun selectTab(@IdRes id: Int): Boolean {
        if (!items.containsKey(id)) return false
        selectedId = id
        items.forEach { (key, item) ->
            val color = if (key == id) checkedColor else normalColor
            item.icon.setColorFilter(color)
            item.label.setTextColor(color)
        }
        return true
    }

    private fun createItem(tab: BottomTab): Item {
        val root = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            contentDescription = tab.label
            setOnClickListener {
                if (!selectTab(tab.id)) return@setOnClickListener
                onTabSelected?.invoke(tab.id)
            }
        }
        val icon = AppCompatImageView(context).apply {
            setImageResource(tab.iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val label = AppCompatTextView(context).apply {
            text = tab.label
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        root.addView(icon, LayoutParams(dp(22), dp(22)))
        root.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        return Item(root, icon, label).also { items[tab.id] = it }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
