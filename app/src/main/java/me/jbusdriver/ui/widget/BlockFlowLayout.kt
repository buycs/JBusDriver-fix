package me.jbusdriver.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import me.jbusdriver.R

/**
 * 第一个子项当作左侧的「一块」(女优页里是 人名 + 头像), 其余子项在它**右侧**流式排布。
 *
 * 规则:
 *  1. 左侧那块独占一列, 从左上角开始; 其余子项从它的右边缘起开始排;
 *  2. 右侧一行放不下就换行, 换行后的 y 仍受左侧那块的高度约束 —— 只在它底部之上继续往右排;
 *  3. 一旦下一行会越过左侧那块的底部, 整体切到「整行模式」: x 回到最左、占满整个宽度往下排。
 *
 * 胶囊区域的上下边界默认 = 整块的上下边界。如果那块本身是「标题 + 内容」两段
 * (比如人名在上面、头像在下面), 用 `app:blockContentTop="true"` 改成与**内容**的上下边界对齐,
 * 这样胶囊顶部就和头像顶部齐平, 溢出时也从头像底部开始换整行。
 *
 * 切到整行模式后第一行与那块底部的空隙用 `app:blockWrapGap` 调, 默认 0(紧贴)。
 *
 * 为什么不能用 [FlowLayout]: FlowLayout 的每行高 = 该行最高的子项, 而左侧那块自己就是第一行里
 * 最高的那个, 于是第二行直接掉到它下面去了 —— 左侧那块右侧的区域会整片空着(实测约 680x300px)。
 *
 * 第一个子项不存在或 GONE 时退化成普通流式排布。
 */
class BlockFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    /** 胶囊区域与左侧那块的「内容」(最后一个子项)对齐, 而不是与整块对齐 */
    private val alignContentTop: Boolean

    /** 换整行模式时, 整行第一行距左侧那块底部的空隙 */
    private val wrapGap: Int

    init {
        context.obtainStyledAttributes(attrs, R.styleable.BlockFlowLayout).let { a ->
            alignContentTop = a.getBoolean(R.styleable.BlockFlowLayout_blockContentTop, false)
            wrapGap = a.getDimensionPixelSize(R.styleable.BlockFlowLayout_blockWrapGap, 0)
            a.recycle()
        }
    }

    private var contentWidth = 0
    private var contentHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }
        walk(width, null)
        setMeasuredDimension(
            resolveSize(contentWidth + paddingStart + paddingEnd, widthMeasureSpec),
            resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        walk(right - left) { child, x, y ->
            child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
        }
    }

    /**
     * 排一遍, 顺带算出内容区尺寸(不含 padding)。
     *
     * @param totalWidth 本控件可用宽度(含 padding)
     * @param place 非 null 时逐个摆放子项, 回调里的 x/y 就是最终左上角
     */
    private fun walk(totalWidth: Int, place: ((View, Int, Int) -> Unit)?) {
        val availWidth = totalWidth - paddingStart - paddingEnd

        // 左侧那块: 先量出来, 它的宽度/高度决定右侧区域的起点和底线
        var blockWidth = 0
        var blockHeight = 0
        var regionTop = paddingTop
        var regionBottom = paddingTop
        if (childCount > 0) {
            val first = getChildAt(0)
            if (first.visibility != View.GONE) {
                val p = first.layoutParams as MarginLayoutParams
                blockWidth = first.measuredWidth + p.leftMargin + p.rightMargin
                blockHeight = first.measuredHeight + p.topMargin + p.bottomMargin
                place?.invoke(first, paddingStart + p.leftMargin, paddingTop + p.topMargin)
                // 块顶(含自己的 topMargin)
                regionTop = paddingTop + p.topMargin
                regionBottom = regionTop + blockHeight - p.topMargin - p.bottomMargin
                if (alignContentTop && first is ViewGroup) {
                    // 只认最后一个可见子项当「内容」: 前面那些算标题, 它们的高度顶下来
                    var lastIndex = -1
                    for (i in 0 until first.childCount) {
                        if (first.getChildAt(i).visibility != View.GONE) lastIndex = i
                    }
                    if (lastIndex >= 0) {
                        var top = regionTop
                        for (i in 0 until lastIndex) {
                            val c = first.getChildAt(i)
                            if (c.visibility == View.GONE) continue
                            val cp = c.layoutParams as MarginLayoutParams
                            top += cp.topMargin + c.measuredHeight + cp.bottomMargin
                        }
                        val last = first.getChildAt(lastIndex)
                        val lastP = last.layoutParams as MarginLayoutParams
                        top += lastP.topMargin
                        regionTop = top
                        regionBottom = top + last.measuredHeight
                    }
                }
            }
        }
        val rightAvail = maxOf(0, availWidth - blockWidth)
        val blockBottom = paddingTop + blockHeight

        var rowStartX = blockWidth   // 当前行的起点(相对 paddingStart)
        var x = 0                    // 当前行已占宽度
        var y = regionTop            // 当前行顶部
        var rowHeight = 0
        var full = false             // 是否已切到整行模式
        var widest = blockWidth

        for (i in 1 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue
            val p = child.layoutParams as MarginLayoutParams
            val cw = child.measuredWidth + p.leftMargin + p.rightMargin
            val ch = child.measuredHeight + p.topMargin + p.bottomMargin

            if (!full) {
                if (x > 0 && x + cw > rightAvail) {
                    // 右侧区域里换行
                    widest = maxOf(widest, rowStartX + x)
                    y += rowHeight
                    x = 0
                    rowHeight = 0
                }
                if (cw > rightAvail || y + p.topMargin + child.measuredHeight > regionBottom) {
                    // 这一行会越过左侧那块(或它的内容)的底部 → 转整行模式, x 回到最左。
                    // 判「越界」只能算到子项的**可视底边**(topMargin + measuredHeight),
                    // 不能把行尾的 bottomMargin 也算进去 —— 那是行与行之间的间距,
                    // 算进去会让「正好贴着区域底部」的最后一行被误判成放不下。
                    full = true
                    rowStartX = 0
                    y = regionBottom + wrapGap
                    x = 0
                    rowHeight = 0
                }
            }
            if (full && x > 0 && x + cw > availWidth) {
                widest = maxOf(widest, x)
                y += rowHeight
                x = 0
                rowHeight = 0
            }

            place?.invoke(child, paddingStart + rowStartX + x + p.leftMargin, y + p.topMargin)
            x += cw
            rowHeight = maxOf(rowHeight, ch)
        }

        widest = maxOf(widest, rowStartX + x)
        contentWidth = widest
        contentHeight = if (rowHeight > 0) {
            maxOf(blockBottom, y + rowHeight) - paddingTop
        } else {
            blockHeight
        }
    }

    // MarginLayoutParams(Context, AttributeSet) 会把 layout_margin* 读进来, 行距/列距就靠子 View 自己的 margin
    override fun generateLayoutParams(attrs: AttributeSet) = MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams() = MarginLayoutParams(
        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
    )

    override fun checkLayoutParams(params: LayoutParams) = params is MarginLayoutParams
}
