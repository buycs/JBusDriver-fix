package me.jbusdriver.ui.fragment

import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.toast
import me.jbusdriver.common.bean.db.Category
import me.jbusdriver.common.bean.db.ForumCategory
import me.jbusdriver.db.service.CategoryService
import me.jbusdriver.mvp.ForumCollectContract
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.ForumPost
import me.jbusdriver.mvp.bean.ForumPostDBType
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.ForumCollectPresenterImpl
import me.jbusdriver.ui.activity.ForumThreadActivity
import me.jbusdriver.ui.holder.CollectDirEditHolder

/**
 * 收藏页的「帖子」页签。结构和 [LinkCollectFragment] 一样(分类树 + 展开收起),
 * 只是条目换成帖子: 点开进帖子正文, 长按能换分类/取消收藏。
 */
class ForumCollectFragment :
    AppBaseRecycleFragment<ForumCollectContract.ForumCollectPresenter, ForumCollectContract.ForumCollectView, CollectLinkWrapper<ForumPost>>(),
    ForumCollectContract.ForumCollectView,
    PageActionTarget {

    override val swipeView: SwipeRefreshLayout? by lazy { view?.findViewById<SwipeRefreshLayout>(R.id.sr_refresh) }
    override val recycleView: RecyclerView by lazy { view!!.findViewById<RecyclerView>(R.id.rv_recycle) }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }
    override val layoutId: Int = R.layout.layout_swipe_recycle

    override val adapter: BaseQuickAdapter<CollectLinkWrapper<ForumPost>, in BaseViewHolder> by lazy {
        object : BaseQuickAdapter<CollectLinkWrapper<ForumPost>, BaseViewHolder>(null) {

            override fun convert(holder: BaseViewHolder, collect: CollectLinkWrapper<ForumPost>) {
                when (holder.itemViewType) {
                    -1 -> {
                        val post = requireNotNull(collect.linkBean)
                        holder.setText(R.id.tv_post_title, post.name)
                    }
                    else -> {
                        setFullSpan(holder)
                        holder.setText(
                            R.id.tv_nav_menu_name,
                            " ${if (collect.isExpanded) "👇" else "👆"} " + collect.category.name
                        )
                    }
                }
            }
        }.apply {
            setOnItemClickListener { _, view, position ->
                val data = this@ForumCollectFragment.adapter.getData().getOrNull(position)
                    ?: return@setOnItemClickListener
                data.linkBean?.let {
                    ForumThreadActivity.open(viewContext, it.link)
                } ?: apply {
                    view.findViewById<TextView>(R.id.tv_nav_menu_name).text = " ${if (data.isExpanded) "👇" else "👆"} " + data.category.name
                    if (data.isExpanded) collapse(adapter.getHeaderLayoutCount() + position) else expand(adapter.getHeaderLayoutCount() + position)
                }
            }

            setOnItemLongClickListener { _, _, position ->
                (this@ForumCollectFragment.adapter.getData().getOrNull(position)?.linkBean)?.let { post ->
                    val action = mutableMapOf<String, (ForumPost) -> Unit>()

                    val category = CategoryService.getById(post.categoryId)
                    if (category != null) {
                        val all = mBasePresenter?.collectGroupMap?.keys ?: emptyList<Category>()
                        val last = all - category
                        if (last.isNotEmpty()) {
                            action["移到分类..."] = { link ->
                                MaterialDialog.Builder(viewContext).title("选择目录")
                                    .items(last.map { it.name })
                                    .itemsCallbackSingleChoice(-1) { _, _, w, _ ->
                                        last.getOrNull(w)?.let {
                                            mBasePresenter?.setCategory(link, it)
                                            mBasePresenter?.onRefresh()
                                        }
                                        return@itemsCallbackSingleChoice true
                                    }.show()
                            }
                        }
                    }

                    action["取消收藏"] = {
                        if (CollectModel.removeCollect(it.convertDBItem())) {
                            toast("取消收藏成功")
                            adapter.data.removeAt(position)
                            adapter.notifyItemRemoved(position)
                        } else {
                            toast("已经取消了")
                        }
                    }

                    MaterialDialog.Builder(viewContext).title(post.name)
                        .items(action.keys)
                        .itemsCallback { _, _, _, text ->
                            action[text]?.invoke(post)
                        }.show()
                }
                true
            }
        }
    }

    private val holder by lazy { CollectDirEditHolder(viewContext, ForumCategory) }

    /** 标签页三点菜单的「修改收藏目录」也走这里 */
    override fun editCollectDir() {
        holder.showDialogWithData(
            mBasePresenter?.collectGroupMap?.keys?.toList() ?: emptyList()
        ) { delActionsParams, addActionsParams ->
            delActionsParams.forEach {
                try {
                    CategoryService.delete(it, ForumPostDBType)
                } catch (e: Exception) {
                    toast("不能删除默认分类")
                }
            }
            addActionsParams.forEach { CategoryService.insert(it) }
            mBasePresenter?.onRefresh()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.findItem(R.id.action_collect_dir_edit)?.setOnMenuItemClickListener {
            editCollectDir()
            true
        }
    }

    override fun createPresenter() = ForumCollectPresenterImpl()

    override fun showContents(data: List<*>) {
        mBasePresenter?.let { p ->
            p.adapterDelegate.needInjectType.onEach {
                if (it == -1) p.adapterDelegate.registerItemType(it, R.layout.layout_forum_post_item)
                else p.adapterDelegate.registerItemType(it, R.layout.layout_menu_op_head)
            }
            adapter.setMultiTypeDelegate(p.adapterDelegate)
        }

        super.showContents(data)
        if (adapter.data.isNotEmpty()) adapter.expand(0)
    }

    companion object {
        fun newInstance() = ForumCollectFragment()
    }
}
