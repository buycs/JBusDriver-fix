package me.jbusdriver.ui.fragment

import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuItem
import me.jbusdriver.R
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenterImpl
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.enums.DataSourceType

/**
 * 底部样式的「影片」页签: 顶部四个子页签复用抽屉那套列表页。
 * 容器本身不请求数据, 所以挂一个空实现的 presenter 就够了。
 */
class MoviePagesFragment : TabViewPagerFragment<BasePresenterImpl<BaseView>, BaseView>() {

    override fun createPresenter(): BasePresenterImpl<BaseView> = BasePresenterImpl()

    override val mTitles: List<String> by lazy { listOf("有码", "无码", "高清", "字幕") }

    override val mFragments: List<Fragment> by lazy {
        listOf(
            DataSourceType.CENSORED,
            DataSourceType.UNCENSORED,
            DataSourceType.GENRE_HD,
            DataSourceType.Sub
        ).map { HomeMovieListFragment.newInstance(it) }
    }

    /** 顶栏上那个跳页图标 + 溢出里的「全部影片」, 底部样式下都收进标签页最右侧的三点菜单 */
    override val tabMenuRes: Int = R.menu.menu_tab_movie

    override fun onPrepareTabMenu(menu: Menu): Boolean {
        val target = currentPageActionTarget() ?: return false
        menu.findItem(R.id.action_jump)?.isVisible =
            AppConfiguration.pageMode == AppConfiguration.PageMode.Page
        menu.findItem(R.id.action_show_all)?.apply {
            isVisible = true
            isChecked = target.isShowAll
        }
        return true
    }

    override fun onTabMenuSelected(item: MenuItem): Boolean {
        val target = currentPageActionTarget() ?: return false
        return when (item.itemId) {
            R.id.action_jump -> {
                target.showJumpPage()
                true
            }
            R.id.action_show_all -> {
                target.setShowAll(!item.isChecked)
                true
            }
            else -> false
        }
    }

    companion object {
        fun newInstance() = MoviePagesFragment()
    }
}
