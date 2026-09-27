package me.jbusdriver.ui.fragment

import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.request.target.DrawableImageViewTarget
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.C
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.LinkListContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.LinkAbsPresenterImpl
import me.jbusdriver.mvp.presenter.MovieLinkPresenterImpl
import me.jbusdriver.ui.activity.SearchResultActivity
import me.jbusdriver.ui.widget.BlockFlowLayout


/**
 * ilink 由跳转链接进入的 /历史记录
 */
class LinkedMovieListFragment : AbsMovieListFragment(), LinkListContract.LinkListView {
    private val link by lazy {
        val link = arguments?.getSerializable(C.BundleKey.Key_1)  as? ILink
            ?: error("no link data ")
        link
    }

    private val isSearch by lazy { link is SearchLink && activity != null && activity is SearchResultActivity }
    private val isHistory by lazy { arguments?.getBoolean(C.BundleKey.Key_2, false) ?: false }

    /** 历史记录那条没有收藏这一说 */
    private val canCollect by lazy { !isHistory || link !is PageLink }

    private var collectMenu: MenuItem? = null
    private var removeCollectMenu: MenuItem? = null

    //region PageActionTarget: 搜索结果标签页三点菜单里的收藏
    override val collected: Boolean?
        get() = if (canCollect) CollectModel.has(link.convertDBItem()) else null

    override fun addCollect() {
        CollectModel.addToCollectForCategory(link.convertDBItem()) {
            collectMenu?.isVisible = false
            removeCollectMenu?.isVisible = true
        }
    }

    override fun removeCollect() {
        if (CollectModel.removeCollect(link.convertDBItem())) {
            collectMenu?.isVisible = true
            removeCollectMenu?.isVisible = false
        }
    }
    //endregion

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        if (!canCollect || handledByTabMenu) return
        val isCollect = collected == true
        collectMenu = menu.add(Menu.NONE, R.id.action_add_movie_collect, 10, "收藏")?.apply {
            setIcon(R.drawable.ic_star_border_white_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = !isCollect
        }
        removeCollectMenu = menu.add(Menu.NONE, R.id.action_remove_movie_collect, 10, "取消收藏")?.apply {
            setIcon(R.drawable.ic_star_white_24dp)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            isVisible = isCollect
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_add_movie_collect -> addCollect()
            R.id.action_remove_movie_collect -> removeCollect()
        }
        return super.onOptionsItemSelected(item)
    }


    override fun initData() {
        if (isSearch) {
            RxBus.toFlowable(SearchWord::class.java).subscribeBy { sea ->
                (mBasePresenter as? LinkAbsPresenterImpl<*>)?.let {
                    (it.linkData as SearchLink).query = sea.query
                    it.onRefresh()
                }
            }.addTo(rxManager)
        }
    }

    override fun gotoSearchResult(query: String) {
        (mBasePresenter as?  LinkAbsPresenterImpl<*>)?.let {
            if (isSearch) {
//                it.linkData.query = query
//                it.onRefresh()
                toast("新搜索 : $query")
                RxBus.post(SearchWord(query))
            } else {
                super.gotoSearchResult(query)
            }
        }
    }

    override fun createPresenter() = MovieLinkPresenterImpl(
        link, arguments?.getBoolean(LinkableListFragment.MENU_SHOW_ALL, false)
            ?: false, isHistory
    )

    override fun <T> showContent(data: T?) {
        if (data is String) {
            //  getLoadAllView(data)?.let { attrViews.put(data,it) }
            tempSaveBundle.putString("temp:load:all", data)
        }

        if (data is IAttr) {
            //attrViews.add(getMovieAttrView(data))
            tempSaveBundle.putSerializable("temp:IAttr", data)
        }
    }

    override fun showContents(data: List<*>) {
        adapter.removeAllHeaderView()
        //load all
        tempSaveBundle.getString("temp:load:all")?.let {
            getLoadAllView(it)?.let { adapter.addHeaderView(it) }
        }

        // movie attr
        (tempSaveBundle.getSerializable("temp:IAttr") as? IAttr)?.let {
            adapter.addHeaderView(getMovieAttrView(it))
        }
        super.showContents(data)

    }

    private fun getMovieAttrView(data: IAttr): View = when (data) {
        is ActressAttrs -> {
            this.viewContext.inflate(R.layout.layout_actress_attr).apply {
                //img
                GlideApp.with(this@LinkedMovieListFragment).load(data.imageUrl.toGlideNoHostUrl)
                    .into(DrawableImageViewTarget(this.findViewById<ImageView>(R.id.iv_actress_avatar)))
                //title
                this.findViewById<TextView>(R.id.tv_attr_title).text = data.title

                // 胶囊交给 BlockFlowLayout 排队: 先在头像右侧那块区域里排, 排不下才换整行
                val flow = this.findViewById<BlockFlowLayout>(R.id.fl_actress_info)
                data.info.forEach {
                    flow.addView(generateTextView().apply {
                        text = it
                        setTextColor(R.color.primaryText.toColorInt())
                        setBackgroundResource(R.drawable.bg_actress_info_capsule)
                        val horizontal = viewContext.dpToPx(12f)
                        val vertical = viewContext.dpToPx(5f)
                        setPadding(horizontal, vertical, horizontal, vertical)
                        layoutParams = ViewGroup.MarginLayoutParams(
                            ViewGroup.MarginLayoutParams.WRAP_CONTENT,
                            ViewGroup.MarginLayoutParams.WRAP_CONTENT
                        ).apply {
                            rightMargin = viewContext.dpToPx(6f)
                            // 行距用 bottomMargin 而不是 topMargin: 这样第一行顶部正好贴着
                            // 头像顶部(与图片顶部齐平), 间距只落在行与行之间
                            bottomMargin = viewContext.dpToPx(2f)
                        }
                    })
                }


//                iv_like_it.setOnClickListener {
//                    MaterialDialog.Builder(it.context).title("演员推荐")
//                            .input("说的什么吧！", null, true) { _, str ->
//                                (link as? ActressInfo)?.let {
//                                    likeIt(it, str.toString())
//                                }
//                            }.positiveText("发送").show()
//                }
            }
        }
        else -> error("current not provide for IAttr $data")
    }

    private fun getLoadAllView(data: String): View? {
        return data.split("：").let { txts ->
            if (txts.size == 2) {
                this.viewContext.inflate(R.layout.layout_load_all).apply {
                    findViewById<TextView>(R.id.tv_info_title).text = txts[0]
                    val spans = txts[1].split("，")
                    require(spans.size == 2)
                    findViewById<TextView>(R.id.tv_change_a).text = spans[0]
                    val tvChangeB = findViewById<TextView>(R.id.tv_change_b)
                    tvChangeB.text = spans[1]
                    tvChangeB.paintFlags = tvChangeB.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                    tvChangeB.setOnClickListener {
                        val showAll = tempSaveBundle.getBoolean(MENU_SHOW_ALL)
                        mBasePresenter?.setAll(!showAll)
                        mBasePresenter?.loadData4Page(1)
                        tempSaveBundle.putBoolean(MENU_SHOW_ALL, !showAll)
                    }
                }
            } else null
        }

    }


    private fun generateTextView() = TextView(this.viewContext).apply {
        textSize = 11.5f
        setTextColor(R.color.secondText.toColorInt())
    }


    /*================================================*/

    companion
    object {
        //电影列表,演员,链接,搜索入口
        fun newInstance(link: ILink) = LinkedMovieListFragment().apply {
            arguments = Bundle().apply {
                putSerializable(C.BundleKey.Key_1, link)
            }
        }
    }
    /*================================================*/
}