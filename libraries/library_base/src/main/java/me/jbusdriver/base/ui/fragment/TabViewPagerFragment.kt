package me.jbusdriver.base.ui.fragment

import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import android.view.View
import me.jbusdriver.base.R
import me.jbusdriver.base.common.AppBaseFragment
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenter

/**
 * Created by Administrator on 2017/7/17 0017.
 */
abstract class TabViewPagerFragment<P : BasePresenter<V>, V : BaseView> : AppBaseFragment<P, V>() {

    abstract val mTitles: List<String>
    abstract val mFragments: List<Fragment>

    override val layoutId = R.layout.base_layout_tab_view_pager

    private val tabLayout: TabLayout get() = requireView().findViewById<TabLayout>(R.id.tabLayout)
    private val vpFragment: ViewPager get() = requireView().findViewById<ViewPager>(R.id.vp_fragment)

    override fun initWidget(rootView: View) {
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
    }

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
}