package me.jbusdriver.mvp.bean

import androidx.annotation.IdRes
import me.jbusdriver.R
import me.jbusdriver.base.common.BaseFragment
import me.jbusdriver.ui.data.enums.DataSourceType
import me.jbusdriver.ui.fragment.*

/*抽屉菜单: 每一项对应 content_main 里的一个 Fragment*/
data class MenuOp(@IdRes val id: Int, val name: String, val initializer: () -> BaseFragment) {

    companion object {
        val Ops: List<MenuOp> by lazy { mine + nav_ma + nav_uncensore + nav_other }


        val mine by lazy {
            listOf(
                MenuOp(R.id.mine_collect, "收藏夹") { MineCollectFragment.newInstance() },
                MenuOp(R.id.mine_history, "最近") { HistoryFragment.newInstance() }
            )
        }

        val nav_ma by lazy {
            listOf(
                MenuOp(R.id.movie_ma, "有碼") { HomeMovieListFragment.newInstance(DataSourceType.CENSORED) },
                MenuOp(R.id.movie_ma_actress, "有碼女優") { ActressListFragment.newInstance(DataSourceType.ACTRESSES) },
                MenuOp(R.id.movie_ma_genre, "有碼類別") { GenrePagesFragment.newInstance(DataSourceType.GENRE) }

            )
        }

        val nav_uncensore by lazy {
            listOf(
                MenuOp(R.id.movie_uncensored, "無碼") { HomeMovieListFragment.newInstance(DataSourceType.UNCENSORED) },
                MenuOp(
                    R.id.movie_uncensored_actress,
                    "無碼女優"
                ) { ActressListFragment.newInstance(DataSourceType.UNCENSORED_ACTRESSES) },
                MenuOp(
                    R.id.movie_uncensored_genre,
                    "無碼類別"
                ) { GenrePagesFragment.newInstance(DataSourceType.UNCENSORED_GENRE) }

            )
        }
        val nav_other by lazy {
            listOf(
                MenuOp(R.id.movie_hd, "高清") { HomeMovieListFragment.newInstance(DataSourceType.GENRE_HD) },
                MenuOp(R.id.movie_sub, "字幕") { HomeMovieListFragment.newInstance(DataSourceType.Sub) },
                MenuOp(R.id.movie_forum, "論壇") { ForumHomeFragment.newInstance() }
            )
        }

        /*底部导航样式的六个页签, id 用 bottom_* 那套, 与抽屉互不冲突*/
        val BottomOps: List<MenuOp> by lazy {
            listOf(
                MenuOp(R.id.bottom_movie, "影片") { MoviePagesFragment.newInstance() },
                MenuOp(R.id.bottom_actress, "女优") { ActressPagesFragment.newInstance() },
                MenuOp(R.id.bottom_search, "搜索") { SearchPageFragment.newInstance() },
                MenuOp(R.id.bottom_collect, "收藏") { MineCollectFragment.newInstance() },
                MenuOp(R.id.bottom_forum, "论坛") { ForumHomeFragment.newInstance() },
                MenuOp(R.id.bottom_setting, "设置") { SettingFragment() }
            )
        }

        fun byId(id: Int): MenuOp? = (Ops + BottomOps).find { it.id == id }
    }
}
