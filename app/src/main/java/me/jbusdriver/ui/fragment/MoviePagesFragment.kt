package me.jbusdriver.ui.fragment

import androidx.fragment.app.Fragment
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.presenter.BasePresenterImpl
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
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

    companion object {
        fun newInstance() = MoviePagesFragment()
    }
}
