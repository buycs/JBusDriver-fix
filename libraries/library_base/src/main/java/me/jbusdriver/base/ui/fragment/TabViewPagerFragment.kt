package me.jbusdriver.base.ui.fragment

import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import me.jbusdriver.base.R
import me.jbusdriver.base.common.AppBaseFragment
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenter
import me.jbusdriver.base.ui.TabOverflowHost

/**
 * Created by Administrator on 2017/7/17 0017.
 */
abstract class TabViewPagerFragment<P : BasePresenter<V>, V : BaseView> : AppBaseFragment<P, V>() {

    abstract val mTitles: List<String>
    abstract val mFragments: List<Fragment>

    override val layoutId = R.layout.base_layout_tab_view_pager

    /**
     * 标签页最右侧那个三点菜单里的项。
     * 为 null 就不摆按钮; 抽屉样式下也不摆 —— 那边顶栏已经带着同一批动作。
     */
    protected open val tabMenuRes: Int? = null

    private val tabLayout: TabLayout get() = requireView().findViewById<TabLayout>(R.id.tabLayout)
    private val vpFragment: ViewPager get() = requireView().findViewById<ViewPager>(R.id.vp_fragment)
    private var overflowButton: View? = null

    override fun initWidget(rootView: View) {
        initTabOverflow(rootView)
        initForViewPager()
    }

    protected fun initForViewPager() {
        mTitles.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        vpFragment.offscreenPageLimit = mTitles.size
        vpFragment.adapter = pagerAdapter
        tabLayout.setupWithViewPager(vpFragment)
        tabLayout.setTabsFromPagerAdapter(pagerAdapter)
        require(mTitles.size == mFragments.size)
        if (mTitles.size >= 5) {
            tabLayout.tabMode = TabLayout.MODE_SCROLLABLE
        }
        // 换子页签后能承接动作的列表页会变(女优页的「类别」是个格子页, 没有跳页), 按钮跟着重算
        vpFragment.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                refreshTabOverflow()
            }
        })
    }

    /**
     * 当前页签下最里层那个子页。
     * 类别页本身还是标签页, 要往里钻一层才能拿到真正的列表页。
     */
    fun currentLeafFragment(): Fragment? {
        var leaf: Fragment? = mFragments.getOrNull(currentPageIndex())
        var depth = 0
        while (leaf is TabViewPagerFragment<*, *> && depth++ < MAX_TAB_DEPTH) {
            leaf = leaf.mFragments.getOrNull(leaf.currentPageIndex())
        }
        return leaf
    }

    private fun currentPageIndex(): Int =
        view?.findViewById<ViewPager>(R.id.vp_fragment)?.currentItem ?: 0

    //region 标签页最右侧的三点菜单
    /** 每次弹出前刷一遍可见性/勾选; 返回 false 表示当前没有可点的项, 按钮也会跟着收起 */
    protected open fun onPrepareTabMenu(menu: Menu): Boolean = true

    /** 三点菜单里的项被点 */
    protected open fun onTabMenuSelected(item: MenuItem): Boolean = false

    private fun initTabOverflow(rootView: View) {
        overflowButton = rootView.findViewById<View>(R.id.ib_tab_overflow)
        overflowButton?.setOnClickListener { showTabMenu() }
        refreshTabOverflow()
    }

    /** 子页签变了之后重算按钮的可见性 */
    protected fun refreshTabOverflow() {
        val button = overflowButton ?: return
        val res = tabMenuRes
        if (res == null || (activity as? TabOverflowHost)?.isBottomShell != true) {
            button.visibility = View.GONE
            return
        }
        button.visibility = if (onPrepareTabMenu(newTabMenu(res))) View.VISIBLE else View.GONE
    }

    /** 只为探一遍可见性, 用完即弃 */
    private fun newTabMenu(res: Int): Menu {
        val anchor = requireNotNull(overflowButton)
        val popup = PopupMenu(anchor.context, anchor)
        popup.inflate(res)
        return popup.menu
    }

    private fun showTabMenu() {
        val anchor = overflowButton ?: return
        val res = tabMenuRes ?: return
        val popup = PopupMenu(anchor.context, anchor)
        popup.inflate(res)
        if (!onPrepareTabMenu(popup.menu)) return
        popup.setOnMenuItemClickListener { onTabMenuSelected(it) }
        popup.show()
    }
    //endregion

    private val pagerAdapter: FragmentPagerAdapter by lazy {
        require(mTitles.size == mFragments.size)
        object : FragmentPagerAdapter(childFragmentManager) {


            override fun getItem(position: Int): Fragment {
                if (mFragments.size >= position) {
                    return mFragments[position]
                } else {
                    error("you must put fragment in mFragments and size equal mTitles")
                }

            }

            override fun getCount(): Int = mTitles.size

            override fun getPageTitle(position: Int): CharSequence = mTitles[position]
        }
    }

    companion object {
        /** 类别页最多再嵌一层标签页, 留点余量防意外自嵌 */
        private const val MAX_TAB_DEPTH = 4
    }
}
