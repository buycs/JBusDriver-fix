package me.jbusdriver.ui.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import me.jbusdriver.R
import me.jbusdriver.base.ui.TabOverflowHost
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

    override val mTitles: List<String> by lazy { listOf("电影", "演员", "帖子", "链接") }

    override val mFragments: List<Fragment> by lazy {
        listOf(
            MovieCollectFragment.newInstance(),
            ActressCollectFragment.newInstance(),
            ForumCollectFragment.newInstance(),
            LinkCollectFragment.newInstance()
        )
    }

    /** 顶栏上那个「修改收藏目录」图标, 底部样式下收进标签页最右侧的三点菜单 */
    override val tabMenuRes: Int = R.menu.menu_collect

    override fun onPrepareTabMenu(menu: Menu): Boolean = currentPageActionTarget() != null

    override fun onTabMenuSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_collect_dir_edit) return false
        val target = currentPageActionTarget() ?: return false
        target.editCollectDir()
        return true
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // 动作在标签页的三点菜单里, 顶栏不再重复摆一份(判宿主, 别把 SettingActivity 那种独立页也带上)
        if ((activity as? TabOverflowHost)?.isBottomShell == true) return
        inflater.inflate(R.menu.menu_collect, menu)
    }


    companion object {
        fun newInstance() = MineCollectFragment()
    }
}
