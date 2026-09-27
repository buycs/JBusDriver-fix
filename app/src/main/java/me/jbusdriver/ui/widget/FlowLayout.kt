package me.jbusdriver.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 子 View 从左往右排, 一行放不下就换下一行。
 *
 * 搜索页的历史词要「一行放多个、满了才换行」, 而它挂在 ScrollView 里,
 * 再嵌一个 wrap_content 的 RecyclerView 量不到稳定高度, 所以自己排。
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    /**
     * 最多排几行, 0 = 不限。超出的子 View 直接不参与布局(等同于不可见)。
     * 搜索历史收起时靠它把多出来的词藏掉。
     */
    var maxRows: Int = 0

    /**
     * 上一次测量因为 [maxRows] 被藏起来的子 View 数。
     * 调用方据此决定要不要摆「展开」入口 —— 在 preDraw 里读, 那时测量已经跑过。
     */
    var overflowCount: Int = 0
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        var rowWidth = 0
        var rowHeight = 0
        var widestRow = 0
        var height = paddingTop + paddingBottom
        var rowIndex = 0
        var overflow = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            if (rowWidth > 0 && rowWidth + childWidth > availWidth) {
                widestRow = maxOf(widestRow, rowWidth)
                height += rowHeight
                rowWidth = 0
                rowHeight = 0
                rowIndex++
            }
            // 行数到顶之后剩下的都藏起来; 注意这里不累加 rowWidth,
            // 于是后面的子项会一直满足换行条件, 一路走到这里被 continue 掉
            if (maxRows > 0 && rowIndex >= maxRows) {
                overflow++
                continue
            }
            rowWidth += childWidth
            rowHeight = maxOf(rowHeight, childHeight)
        }
        widestRow = maxOf(widestRow, rowWidth)
        height += rowHeight
        overflowCount = overflow

        setMeasuredDimension(
            resolveSize(widestRow + paddingStart + paddingEnd, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val availWidth = right - left - paddingStart - paddingEnd
        var x = paddingStart
        var y = paddingTop
        var rowHeight = 0
        var rowIndex = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val params = child.layoutParams as MarginLayoutParams
            val childWidth = child.measuredWidth + params.leftMargin + params.rightMargin
            val childHeight = child.measuredHeight + params.topMargin + params.bottomMargin
            if (rowHeight > 0 && x + childWidth - paddingStart > availWidth) {
                x = paddingStart
                y += rowHeight
                rowHeight = 0
                rowIndex++
            }
            if (maxRows > 0 && rowIndex >= maxRows) continue
            val childLeft = x + params.leftMargin
            val childTop = y + params.topMargin
            child.layout(
                childLeft, childTop,
                childLeft + child.measuredWidth, childTop + child.measuredHeight
            )
            x += childWidth
            rowHeight = maxOf(rowHeight, childHeight)
        }
    }

    // MarginLayoutParams(Context, AttributeSet) 会把 layout_margin* 读进来, 行距就靠子 View 自己的 margin
    override fun generateLayoutParams(attrs: AttributeSet) = MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams() = MarginLayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
    )

    override fun checkLayoutParams(params: LayoutParams) = params is MarginLayoutParams
}
