package me.jbusdriver.ui.holder

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import kotlinx.android.synthetic.main.layout_detail_forum_posts.view.*
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.inflate
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.bean.ForumPost
import me.jbusdriver.ui.activity.ForumThreadActivity

/**
 * 論壇熱帖
 */
class ForumPostsHolder(context: Context) : BaseHolder(context) {

    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_forum_posts).apply {
                rv_recycle_forum_posts.layoutManager =
                    LinearLayoutManager(it, LinearLayoutManager.HORIZONTAL, false)
                forumAdapter.bindToRecyclerView(rv_recycle_forum_posts)
                rv_recycle_forum_posts.isNestedScrollingEnabled = true
                forumAdapter.setOnItemClickListener { _, v, position ->
                    forumAdapter.data.getOrNull(position)?.let { post ->
                        ForumThreadActivity.open(v.context, post.link)
                    }
                }
            }
        } ?: error("context ref is finish")
    }

    private val forumAdapter: BaseQuickAdapter<ForumPost, BaseViewHolder> by lazy {
        object : BaseQuickAdapter<ForumPost, BaseViewHolder>(R.layout.layout_detail_forum_posts_item) {
            override fun convert(holder: BaseViewHolder, item: ForumPost) {
                GlideApp.with(holder.itemView.context)
                    .load(item.image.toGlideNoHostUrl)
                    .into(holder.getView(R.id.iv_forum_post_image))
                holder.setText(R.id.tv_forum_post_title, item.name)
            }
        }
    }

    fun init(forumPosts: List<ForumPost>) {
        if (forumPosts.isEmpty()) view.tv_movie_forum_none_tip.visibility = View.VISIBLE
        else forumAdapter.setNewData(forumPosts)
    }
}