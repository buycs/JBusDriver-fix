package me.jbusdriver.ui.adapter

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.inflate
import me.jbusdriver.common.toGlideUrlReferedBy
import me.jbusdriver.mvp.bean.ForumFloor
import me.jbusdriver.mvp.bean.FORUM_SITE_HOST
import me.jbusdriver.mvp.bean.ForumSegment

/**
 * 樓層列表。一段一個子 View 挂在楼层容器里: 楼层数量一屏十几个, 段落再拆成
 * RecyclerView 的 item 只会让排版分支更绕, 也拿不到多少复用收益。
 */
class ForumFloorAdapter(private val onImageClick: (ForumFloor, Int) -> Unit) :
    BaseQuickAdapter<ForumFloor, BaseViewHolder>(R.layout.layout_forum_floor) {

    override fun convert(holder: BaseViewHolder, item: ForumFloor) {
        holder.setText(R.id.tv_floor_no, item.floorNo)
        holder.setText(R.id.tv_floor_author, item.author)
        holder.setText(R.id.tv_floor_time, item.time)
        render(holder.getView(R.id.ll_floor_content), item)
    }

    private fun render(container: LinearLayout, floor: ForumFloor) {
        container.removeAllViews()
        val context = container.context
        floor.segments.forEach { segment ->
            // 必须把 container 当 parent 传进去: inflate(res, null) 会丢掉根节点的
            // LayoutParams, 图片那种靠固定高度撑开的子布局会直接塌成 0 高
            when (segment) {
                is ForumSegment.Text -> {
                    val view = context.inflate(R.layout.layout_forum_text, container) as TextView
                    bindText(view, segment.content)
                    container.addView(view)
                }
                is ForumSegment.Quote -> {
                    val view = context.inflate(R.layout.layout_forum_quote, container)
                    val header = view.findViewById<TextView>(R.id.tv_forum_quote_header)
                    header.text = segment.header
                    header.visibility = if (segment.header.isBlank()) View.GONE else View.VISIBLE
                    bindText(view.findViewById(R.id.tv_forum_quote), segment.content)
                    container.addView(view)
                }
                is ForumSegment.Image -> {
                    val view = context.inflate(R.layout.layout_forum_image, container)
                    val image = view.findViewById<ImageView>(R.id.iv_forum_image)
                    image.setOnClickListener { onImageClick(floor, segment.imageIndex) }
                    GlideApp.with(image)
                        .load(segment.thumb.toGlideUrlReferedBy(FORUM_SITE_HOST))
                        .error(R.drawable.ic_image_error)
                        .into(image)
                    container.addView(view)
                }
            }
        }
    }

    private fun bindText(view: TextView, content: CharSequence) {
        view.text = content
        val linked = content is Spannable &&
                content.getSpans(0, content.length, URLSpan::class.java).isNotEmpty()
        if (linked) {
            // 链接要能点, 就得交回 movementMethod, 这时放弃长按选词
            view.setTextIsSelectable(false)
            view.movementMethod = LinkMovementMethod.getInstance()
        }
    }
}
