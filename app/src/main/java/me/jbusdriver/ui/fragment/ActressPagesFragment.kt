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
 * 底部样式的「女优」页签: 有码/无码女优是列表页, 有码/无码类别各自再带一层类别子页签。
 */
class ActressPagesFragment : TabViewPagerFragment<BasePresenterImpl<BaseView>, BaseView>() {

    override fun createPresenter(): BasePresenterImpl<BaseView> = BasePresenterImpl()

    override val mTitles: List<String> by lazy {
        listOf("有码女优", "无码女优", "有码类别", "无码类别")
    }

    override val mFragments: List<Fragment> by lazy {
        listOf(
            ActressListFragment.newInstance(DataSourceType.ACTRESSES),
            ActressListFragment.newInstance(DataSourceType.UNCENSORED_ACTRESSES),
            GenrePagesFragment.newInstance(DataSourceType.GENRE),
            GenrePagesFragment.newInstance(DataSourceType.UNCENSORED_GENRE)
        )
    }

    /** 顶栏上那个跳页图标, 底部样式下收进标签页最右侧的三点菜单 */
    override val tabMenuRes: Int = R.menu.menu_tab_jump

    override fun onPrepareTabMenu(menu: Menu): Boolean {
        val target = currentPageActionTarget() ?: return false
        menu.findItem(R.id.action_jump)?.isVisible =
            AppConfiguration.pageMode == AppConfiguration.PageMode.Page
        return true
    }

    override fun onTabMenuSelected(item: MenuItem): Boolean {
        val target = currentPageActionTarget() ?: return false
        return when (item.itemId) {
            R.id.action_jump -> {
                target.showJumpPage()
                true
            }
            else -> false
        }
    }

    companion object {
        fun newInstance() = ActressPagesFragment()
    }
}
