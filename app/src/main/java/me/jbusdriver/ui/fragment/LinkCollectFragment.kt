package me.jbusdriver.ui.fragment

import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.Menu
import android.view.MenuInflater
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.dpToPx
import me.jbusdriver.base.toast
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.common.bean.db.Category
import me.jbusdriver.common.bean.db.LinkCategory
import me.jbusdriver.db.service.CategoryService
import me.jbusdriver.mvp.LinkCollectContract
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.SearchLink
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.LinkCollectPresenterImpl
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.activity.SearchResultActivity
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import me.jbusdriver.ui.holder.CollectDirEditHolder

class LinkCollectFragment :
    AppBaseRecycleFragment<LinkCollectContract.LinkCollectPresenter, LinkCollectContract.LinkCollectView, CollectLinkWrapper<ILink>>(),
    LinkCollectContract.LinkCollectView,
    PageActionTarget {

    override val swipeView: SwipeRefreshLayout? by lazy { view?.findViewById<SwipeRefreshLayout>(R.id.sr_refresh) }
    override val recycleView: RecyclerView by lazy { view!!.findViewById<RecyclerView>(R.id.rv_recycle) }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }
    override val adapter: BaseQuickAdapter<CollectLinkWrapper<ILink>, in BaseViewHolder> by lazy {
        object : BaseQuickAdapter<CollectLinkWrapper<ILink>, BaseViewHolder>(null) {

            override fun convert(holder: BaseViewHolder, collect: CollectLinkWrapper<ILink>) {
                when (holder.itemViewType) {
                    -1 -> {
                        val item = requireNotNull(collect.linkBean)
                        val des = item.des.split(" ")
                        holder.getView<TextView>(R.id.tv_head_value)?.apply {
                            setTextColor(ResourcesCompat.getColor(this@apply.resources, R.color.colorPrimaryDark, null))
                            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG

                        }
                        val dp8 = mContext.dpToPx(8f)
                        holder.itemView.setPadding(dp8 * 2, dp8, dp8 * 2, dp8)
                        holder.setText(R.id.tv_head_name, des.firstOrNull())
                            .setText(R.id.tv_head_value, des.lastOrNull())

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
                val data = this@LinkCollectFragment.adapter.getData().getOrNull(position)
                    ?: return@setOnItemClickListener
                data.linkBean?.let {
                    if (it is SearchLink) {
                        SearchResultActivity.start(viewContext, it.query)
                    } else MovieListActivity.start(viewContext, it)

                } ?: apply {
                    view.findViewById<TextView>(R.id.tv_nav_menu_name).text = " ${if (data.isExpanded) "👇" else "👆"} " + data.category.name
                    if (data.isExpanded) collapse(adapter.getHeaderLayoutCount() + position) else expand(adapter.getHeaderLayoutCount() + position)
                }
            }

            setOnItemLongClickListener { adapter, _, position ->
                (this@LinkCollectFragment.adapter.getData().getOrNull(position)?.linkBean)?.let { link ->
                    val action = LinkMenu.linkActions.toMutableMap()
                    action.remove("收藏")
                    val category = CategoryService.getById(link.categoryId)
                    if (category != null) {
                        val all = mBasePresenter?.collectGroupMap?.keys ?: emptyList<Category>()
                        val last = all - category
                        if (last.isNotEmpty()) {
                            action.put("移到分类...") { link ->
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
                    MaterialDialog.Builder(viewContext).content(link.des)
                        .items(action.keys)
                        .itemsCallback { _, _, _, text ->
                            action[text]?.invoke(link)
                        }.show()

                }
                true
            }


        }
    }
    override val layoutId: Int = R.layout.layout_swipe_recycle

    override fun createPresenter() = LinkCollectPresenterImpl()
    private val holder by lazy { CollectDirEditHolder(viewContext, LinkCategory) }

    /** 标签页三点菜单的「修改收藏目录」也走这里 */
    override fun editCollectDir() {
        holder.showDialogWithData(
            mBasePresenter?.collectGroupMap?.keys?.toList()
                ?: emptyList()
        ) { delActionsParams, addActionsParams ->
            if (delActionsParams.isNotEmpty()) {
                delActionsParams.forEach {
                    try {
                        CategoryService.delete(it, 3) //link 数据库中默认为3 具体可以有3..6
                    } catch (e: Exception) {
                        toast("不能删除默认分类")
                    }
                }
            }

            if (addActionsParams.isNotEmpty()) {
                addActionsParams.forEach {
                    CategoryService.insert(it)
                }
            }
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


    override fun showContents(data: List<*>) {
        mBasePresenter?.let { p ->
            p.adapterDelegate.needInjectType.onEach {
                if (it == -1) p.adapterDelegate.registerItemType(it, R.layout.layout_header_item) //默认注入类型0，即actress
                else p.adapterDelegate.registerItemType(it, R.layout.layout_menu_op_head) //头部，可以做特化
            }
            adapter.setMultiTypeDelegate(p.adapterDelegate)
        }

        super.showContents(data)
        if (adapter.data.isNotEmpty()) adapter.expand(0)
    }


    companion object {
        fun newInstance() = LinkCollectFragment()
    }
}