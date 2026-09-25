package me.jbusdriver.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * 子 View 从左往右排, 一行放不下就换下一行。
 *
 * 女优页的个人信息要「胶囊一行放多个、满了换行」, 而它本身在 RecyclerView 的头部视图里,
 * 再嵌一个 wrap_content 的 RecyclerView 量不到稳定高度, 所以自己排。
 */
class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        var rowWidth = 0
        var rowHeight = 0
        var widestRow = 0
        var height = paddingTop + paddingBottom

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
            }
            rowWidth += childWidth
            rowHeight = maxOf(rowHeight, childHeight)
        }
        widestRow = maxOf(widestRow, rowWidth)
        height += rowHeight

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
            }
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
