package me.jbusdriver.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.mvp.bean.ForumThreadSummary

class ForumThreadAdapter :
    BaseQuickAdapter<ForumThreadSummary, BaseViewHolder>(R.layout.layout_forum_thread_item) {

    override fun convert(holder: BaseViewHolder, item: ForumThreadSummary) {
        holder.setText(R.id.tv_thread_title, item.title)
        holder.setText(R.id.tv_thread_board, item.board)
        holder.setGone(R.id.tv_thread_board, item.board.isNotBlank())
        holder.setText(R.id.tv_thread_author, item.author)
        holder.setGone(R.id.tv_thread_author, item.author.isNotBlank())
        holder.setText(R.id.tv_thread_time, item.time)
        holder.setText(R.id.tv_thread_count, "${item.replies} 回復 · ${item.views} 瀏覽")
    }
}
