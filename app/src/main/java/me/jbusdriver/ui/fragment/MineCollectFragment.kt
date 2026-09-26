package me.jbusdriver.ui.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuInflater
import me.jbusdriver.R
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
import me.jbusdriver.mvp.MineCollectContract
import me.jbusdriver.mvp.presenter.MineCollectPresenterImpl

/**
 * since 1.1 remove info menu
 */
class MineCollectFragment :
    TabViewPagerFragment<MineCollectContract.MineCollectPresenter, MineCollectContract.MineCollectView>(),
    MineCollectContract.MineCollectView {
    override fun createPresenter() = MineCollectPresenterImpl()

    override val mTitles: List<String> by lazy { listOf("电影", "演员", "链接") }

    override val mFragments: List<Fragment> by lazy {
        listOf(
            MovieCollectFragment.newInstance(),
            ActressCollectFragment.newInstance(),
            LinkCollectFragment.newInstance()
        )
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_collect, menu)
    }


    companion object {
        fun newInstance() = MineCollectFragment()
    }
}