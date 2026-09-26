package me.jbusdriver.ui.fragment

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.chad.library.adapter.base.util.MultiTypeDelegate
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.C
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.common.isEndWithXyzHost
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.bean.GridColumnChangeEvent
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieDetailActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.contextMenu.LinkMenu
import me.jbusdriver.ui.data.enums.DataSourceType


abstract class AbsMovieListFragment : LinkableListFragment<Movie>() {

    /*
        CENSORED("有碼", "/page/"), //有码

    GENRE("有碼類別"), //类别

    ACTRESSES("有碼女優"), //女优

     */
    override val type: DataSourceType by lazy {
        arguments?.getSerializable(MOVIE_LIST_DATA_TYPE) as? DataSourceType ?: let {
            (arguments?.getSerializable(C.BundleKey.Key_1) as? ILink)?.let { link ->

                val path = link.link.urlPath
                val type = when {
                    link.link.urlHost.isEndWithXyzHost -> {
                        //xyz
                        when {
                            path.startsWith("genre") -> DataSourceType.GENRE
                            path.startsWith("star") -> DataSourceType.ACTRESSES
                            else -> DataSourceType.CENSORED
                        }

                    }
                    else -> {
                        when {
                            path.startsWith("uncensored") -> {
                                //无码

                                when {
                                    path.startsWith("uncensored/genre") -> DataSourceType.GENRE
                                    path.startsWith("uncensored/star") -> DataSourceType.ACTRESSES
                                    else -> DataSourceType.CENSORED
                                }
                            }
                            else -> {
                                //有码
                                when {
                                    path.startsWith("genre") -> DataSourceType.GENRE
                                    path.startsWith("star") -> DataSourceType.ACTRESSES
                                    else -> DataSourceType.CENSORED
                                }
                            }
                        }

                    }

                }
                type

            } ?: DataSourceType.CENSORED
        }
    }


    /**
     * 网格用 GridLayoutManager 而不是 StaggeredGrid: 后者每列各自往上顶, 同一行的条目上下边对不齐。
     * 一列时它就是原来的按行显示。
     */
    override val layoutManager: RecyclerView.LayoutManager by lazy {
        GridLayoutManager(viewContext, AppConfiguration.gridColumn)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 订阅要和 onDestroyView 里的 rxManager.clear() 配对着注册, 放 onCreate 的话
        // 视图重建一次之后收不到事件, 改列数就没反应了
        RxBus.toFlowable(GridColumnChangeEvent::class.java)
            .subscribeBy { applyGridColumn(it.columns) }
            .addTo(rxManager)
        // 视图销毁期间发的事件是漏掉的, 重建时按当前配置补一次
        applyGridColumn(AppConfiguration.gridColumn)
    }

    /** 设置里改了列数: 就地换 spanCount, 条目布局类型也跟着变所以整表重绑 */
    private fun applyGridColumn(columns: Int) {
        (layoutManager as? GridLayoutManager)?.spanCount = columns
        adapter.notifyDataSetChanged()
    }

    override val adapter: BaseQuickAdapter<Movie, in BaseViewHolder>  by lazy {
        object : BaseQuickAdapter<Movie, BaseViewHolder>(null) {

            private val Movie.isInValid
                inline get() = TextUtils.isEmpty(code) && TextUtils.isEmpty(link)

            init {

                multiTypeDelegate = object : MultiTypeDelegate<Movie>() {
                    override fun getItemType(t: Movie): Int = when {
                        t.isInValid -> TYPE_SECTION
                        AppConfiguration.gridColumn > AppConfiguration.GridColumn.LINE -> TYPE_GRID
                        else -> TYPE_LINE
                    }
                }

                multiTypeDelegate
                    .registerItemType(TYPE_SECTION, R.layout.layout_pager_section_item)
                    .registerItemType(TYPE_LINE, R.layout.layout_page_line_movie_item)
                    .registerItemType(TYPE_GRID, R.layout.layout_grid_movie_item)

            }

            private val dp8 by lazy { this@AbsMovieListFragment.viewContext.dpToPx(8f) }
            private val backColors = listOf(0xff2195f3.toInt(), 0xff4caf50.toInt(), 0xffff0030.toInt()) //蓝,绿,红

            private fun genLp() = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                this@AbsMovieListFragment.viewContext.dpToPx(24f)
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }

            override fun convert(holder: BaseViewHolder, item: Movie) {
                when (holder.itemViewType) {
                    TYPE_SECTION -> {
                        holder.setText(R.id.tv_page_num, item.title)
                        val currentPage = item.title.toIntOrNull()
                        if (currentPage != null) {
                            holder.setGone(
                                R.id.tv_load_prev, mBasePresenter?.isPrevPageLoaded(currentPage)
                                    ?: true
                            )
                            holder.getView<View>(R.id.tv_load_prev)?.setOnClickListener {
                                mBasePresenter?.jumpToPage(currentPage - 1)
                            }
                        }
                    }
                    TYPE_LINE, TYPE_GRID -> {
                        // 网格条目没有左侧竖线和标签行, 这两处只给按行布局绑
                        if (holder.itemViewType == TYPE_LINE) {
                            when (pageMode) {
                                AppConfiguration.PageMode.Page -> {
                                    holder.setGone(R.id.v_line, true)
                                }
                                AppConfiguration.PageMode.Normal -> {
                                    holder.setGone(R.id.v_line, false)
                                }
                            }
                        }


                        holder.setText(R.id.tv_movie_title, item.title)
                            .setText(R.id.tv_movie_date, item.date)
                            .setText(R.id.tv_movie_code, item.code)


                        GlideApp.with(this@AbsMovieListFragment).load(item.imageUrl.toGlideNoHostUrl)
                            .placeholder(R.drawable.ic_place_holder)
                            .error(R.drawable.ic_place_holder).centerCrop()
                            .into(DrawableImageViewTarget(holder.getView(R.id.iv_movie_img)))


                        holder.getView<LinearLayout>(R.id.ll_movie_hot)?.let { ll_movie_hot ->
                            with(ll_movie_hot) {
                                this.removeAllViews()
                                item.tags?.mapIndexed { index, tag ->
                                    (viewContext.inflate(R.layout.tv_movie_tag) as TextView).let {
                                        it.text = tag
                                        it.setPadding(dp8, 0, dp8, 0)
                                        it.background = GradientDrawable().apply {
                                            setColor(
                                                backColors.getOrNull(index % 3)
                                                    ?: backColors.first()
                                            )
                                            cornerRadius = dp8 * 2f
                                        }
                                        it.layoutParams = genLp().apply { leftMargin = dp8 }
                                        this.addView(it)
                                    }
                                }

                            }
                        }
                        holder.getView<View>(R.id.card_movie_item)?.let {
                            it.setOnClickListener {
                                MovieDetailActivity.start(viewContext, item)
                            }
                            it.setOnLongClickListener {

                                val action =
                                    (if (CollectModel.has(item.convertDBItem())) LinkMenu.movieActions.minus("收藏")
                                    else LinkMenu.movieActions.minus("取消收藏")).toMutableMap()
                                val ac = action.remove("收藏")
                                if (ac != null) {
                                    action["收藏到分类..."] = ac
                                }
                                MaterialDialog.Builder(viewContext).title(item.code)
                                    .content(item.title)
                                    .items(action.keys)
                                    .itemsCallback { _, _, _, text ->
                                        action[text]?.invoke(item)
                                    }
                                    .show()
                                return@setOnLongClickListener true
                            }

                        }

                    }

                }


            }
        }.apply {
            /*
             * 分页分隔条要占满一行。GridLayoutManager 的 SpanSizeLookup 在 adapter 挂上 RecyclerView 时
             * 会被 BRVAH 换成它自己的, 直接给 layoutManager 设会被覆盖, 只能走这个回调。
             * 回调里的 position 已经减掉表头数, 所以按数据下标问类型。
             */
            setSpanSizeLookup { gridManager, position ->
                val type = multiTypeDelegate?.getDefItemViewType(data, position) ?: 0
                if (type == TYPE_SECTION) gridManager.spanCount else 1
            }
            setOnItemClickListener { adapter, _, position ->
                (adapter.data.getOrNull(position) as? Movie)?.let {
                    when (adapter.multiTypeDelegate?.getDefItemViewType(adapter.data, position)) {
                        -1 -> {
                            mBasePresenter?.currentPageInfo?.let {
                                if (it.referPages.isNotEmpty()) showPageDialog(it)
                            }

                        }
                        else -> {

                        }
                    }
                }
            }


        }
    }


    override fun insertData(pos: Int, data: List<*>) {
        adapter.addData(pos, data as List<Movie>)
    }

    override fun moveTo(pos: Int) {
        layoutManager.scrollToPosition(adapter.getHeaderLayoutCount() + pos)
    }


    //    override fun toString(): String = "$type :" + super.toString()

    companion object {
        const val MOVIE_LIST_DATA_TYPE = "movie:list:data:type"

        /** 分页模式下的页码分隔条 */
        private const val TYPE_SECTION = -1

        /** 一行一条, 也就是原来的样式 */
        private const val TYPE_LINE = 1
        private const val TYPE_GRID = 2
    }

}