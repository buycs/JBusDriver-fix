package me.jbusdriver.ui.fragment

import android.os.Bundle
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.*
import android.text.InputType
import android.text.TextUtils
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.afollestad.materialdialogs.MaterialDialog
import com.xw.repo.BubbleSeekBar
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.AppBaseRecycleFragment
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.ui.TabOverflowHost
import me.jbusdriver.mvp.LinkListContract
import me.jbusdriver.mvp.bean.PageChangeEvent
import me.jbusdriver.ui.activity.SearchResultActivity
import me.jbusdriver.ui.data.AppConfiguration

abstract class LinkableListFragment<T> :
    AppBaseRecycleFragment<LinkListContract.LinkListPresenter, LinkListContract.LinkListView, T>(),
    LinkListContract.LinkListView,
    PageActionTarget {

    override val layoutId: Int = R.layout.layout_swipe_recycle

    override val swipeView: SwipeRefreshLayout? by lazy { view?.findViewById<SwipeRefreshLayout>(R.id.sr_refresh) }
    override val recycleView: RecyclerView  by lazy { view!!.findViewById<RecyclerView>(R.id.rv_recycle) }
    override val layoutManager: RecyclerView.LayoutManager by lazy { LinearLayoutManager(viewContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindRx()
    }

    private fun bindRx() {
        RxBus.toFlowable(PageChangeEvent::class.java)
            .subscribeBy(onNext = {
                activity?.invalidateOptionsMenu() //刷新菜单
                mBasePresenter?.onRefresh()
            }).addTo(rxManager)
    }


    override fun restoreState(bundle: Bundle) {
        super.restoreState(bundle)
        val all = bundle.getBoolean(MENU_SHOW_ALL, false)
        tempSaveBundle.putBoolean(MENU_SHOW_ALL, all)
        if (all) {
            mBasePresenter?.setAll(true)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.main, menu)
        // 底部样式下顶栏收起来了, SearchView 的动作视图可能没建, 别硬转
        val searchView = menu.findItem(R.id.action_search)
            ?.let { MenuItemCompat.getActionView(it) as? SearchView } ?: return

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (TextUtils.isEmpty(query)) toast("关键字不能为空!")
                gotoSearchResult(query.orEmpty())

                return true
            }

            override fun onQueryTextChange(newText: String?) = false
        })

    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu) //menu before show
        // 动作已经搬进标签页最右侧的三点菜单, 顶栏不再重复摆一份
        val inTopBar = !handledByTabMenu
        menu.findItem(R.id.action_show_all)?.apply {
            isChecked = isShowAll
            isVisible = inTopBar
        }
        menu.findItem(R.id.action_jump)?.let {
            it.isVisible = inTopBar && AppConfiguration.pageMode == AppConfiguration.PageMode.Page
        }
    }

    /**
     * 页面动作已经搬进标签页三点菜单的宿主(底部外壳), 顶栏不再重复摆一份。
     * 判宿主而不是判 AppConfiguration.uiMode: MovieListActivity 这类独立 Activity
     * 在底部样式下也照样用顶栏, 不能把它那份一起藏了。
     */
    protected val handledByTabMenu: Boolean
        get() = (activity as? TabOverflowHost)?.isBottomShell == true

    protected open fun gotoSearchResult(query: String) {
        SearchResultActivity.start(viewContext, query)
    }

    //region PageActionTarget: 标签页三点菜单的动作
    override fun showJumpPage() {
        mBasePresenter?.currentPageInfo?.let { showPageDialog(it) }
    }

    override fun setShowAll(showAll: Boolean) {
        mBasePresenter?.setAll(showAll)
        mBasePresenter?.loadData4Page(1)
        tempSaveBundle.putBoolean(MENU_SHOW_ALL, showAll)
    }

    override val isShowAll: Boolean
        get() = tempSaveBundle.getBoolean(MENU_SHOW_ALL, false)
    //endregion

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the CENSORED/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        when (id) {
            R.id.action_show_all -> {
                item.isChecked = !item.isChecked
                if (item.isChecked) item.title = "已发布" else item.title = "全部电影"  /*false : 已发布的 ,true :全部*/
                setShowAll(item.isChecked)
            }
            R.id.action_jump -> showJumpPage()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(MENU_SHOW_ALL, tempSaveBundle.getBoolean(MENU_SHOW_ALL, false))
    }


    /*================================================*/
//    override val type by lazy {
//        arguments?.getSerializable(C.BundleKey.Key_1) as? DataSourceType ?: DataSourceType.CENSORED
//    }

    protected fun showPageDialog(info: PageInfo) {
        if (info.referPages.isEmpty()) return
        if (info.referPages.size == 1 && info.referPages.first() == 1) {
            toast("当前共一页")
            return
        }
        val seekView = viewContext.inflate(R.layout.layout_seek_page)
        seekView.findViewById<BubbleSeekBar>(R.id.bsb_seek_page)?.apply {

            try {
                val max = this.javaClass.getDeclaredField("mMax")
                max?.isAccessible = true
                max?.setFloat(this, info.referPages.last().toFloat())

                this.javaClass.getDeclaredMethod("initConfigByPriority").also {
                    it.isAccessible = true
                    it.invoke(this)
                }

                setProgress(info.activePage.toFloat())
                this.post {
                    this.invalidate()
                    this.requestLayout()
                }
            } catch (e: Exception) {
                KLog.w("error :$e")
            }
        }
        MaterialDialog.Builder(viewContext).customView(seekView, false)
            .neutralText("输入页码").onNeutral { dialog, _ ->
                showEditDialog(info)
                dialog.dismiss()
            }.positiveText("跳转").onPositive { _, _ ->
                seekView.findViewById<BubbleSeekBar>(R.id.bsb_seek_page)?.progress?.let {
                    mBasePresenter?.jumpToPage(it)
                    adapter.notifyLoadMoreToLoading()
                }
            }.show()

    }

    private fun showEditDialog(info: PageInfo) {
        MaterialDialog.Builder(viewContext).title("输入页码:")
            .input("输入跳转页码", null, false) { dialog, input ->
                input.toString().toIntOrNull()?.let {
                    if (it < 1) {
                        toast("必须输入大于0的整数!")
                        return@input
                    }
                    mBasePresenter?.jumpToPage(it)
                    dialog.dismiss()
                } ?: let {
                    toast("必须输入数字!")
                }
            }
            .autoDismiss(false)
            .inputType(InputType.TYPE_CLASS_NUMBER)
            .neutralText("选择页码").onNeutral { dialog, _ ->
                showPageDialog(info)
                dialog.dismiss()
            }.show()
    }


    override val pageMode: Int
        get() = AppConfiguration.pageMode

    companion object {
        const val MENU_SHOW_ALL = "action_show_all"
    }
}