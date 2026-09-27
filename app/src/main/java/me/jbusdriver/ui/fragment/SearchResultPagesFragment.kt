package me.jbusdriver.ui.fragment

import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuItem
import me.jbusdriver.R
import me.jbusdriver.base.common.C
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
import me.jbusdriver.mvp.MineCollectContract
import me.jbusdriver.mvp.bean.SearchLink
import me.jbusdriver.mvp.presenter.MineCollectPresenterImpl
import me.jbusdriver.ui.data.enums.SearchType

/**
 * Created by Administrator on 2017/7/17 0017.
 */
class SearchResultPagesFragment :
    TabViewPagerFragment<MineCollectContract.MineCollectPresenter, MineCollectContract.MineCollectView>(),
    MineCollectContract.MineCollectView {

    private val searchWord by lazy { arguments?.getString(C.BundleKey.Key_1) ?: error("must set search word") }

    override fun createPresenter() = MineCollectPresenterImpl()

    override val mTitles: List<String> by lazy { SearchType.values().map { it.title } }

    override val mFragments: List<Fragment> by lazy {
        SearchType.values().map {
            if (it == SearchType.ACTRESS) {
                ActressListFragment.newInstance(SearchLink(it, searchWord))
            } else {
                LinkedMovieListFragment.newInstance(SearchLink(it, searchWord))
            }
        }
    }

    /**
     * 底部样式下搜索页顶栏整条收起, 原本挂在顶栏上的「收藏 / 取消收藏」两个星标搬到这里。
     * 抽屉样式下这里不摆按钮(宿主不是底部外壳), 那套照旧走 SearchResultActivity 的顶栏。
     */
    override val tabMenuRes: Int = R.menu.menu_tab_search

    /** 结果区就贴在搜索框下面, 标签栏顶部切圆角跟搜索框的胶囊呼应 */
    override val roundedTabBar: Boolean = true

    override fun onPrepareTabMenu(menu: Menu): Boolean {
        val collected = currentPageActionTarget()?.collected ?: return false
        menu.findItem(R.id.action_add_movie_collect)?.isVisible = !collected
        menu.findItem(R.id.action_remove_movie_collect)?.isVisible = collected
        return true
    }

    override fun onTabMenuSelected(item: MenuItem): Boolean {
        val target = currentPageActionTarget() ?: return false
        return when (item.itemId) {
            R.id.action_add_movie_collect -> {
                target.addCollect()
                true
            }
            R.id.action_remove_movie_collect -> {
                target.removeCollect()
                true
            }
            else -> false
        }
    }
}
