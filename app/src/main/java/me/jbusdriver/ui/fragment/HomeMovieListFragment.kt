package me.jbusdriver.ui.fragment

import android.os.Bundle
import me.jbusdriver.mvp.LinkListContract
import me.jbusdriver.mvp.bean.PageLink
import me.jbusdriver.mvp.presenter.HomeMovieListPresenterImpl
import me.jbusdriver.ui.data.enums.DataSourceType


/**
 * Created by Administraor on 2017/4/9.
 */
class HomeMovieListFragment : AbsMovieListFragment(), LinkListContract.LinkListView {
    override fun createPresenter() = HomeMovieListPresenterImpl(
        type,
        PageLink(1, "", type.url) /*PageLink只是占位,列表真正的地址由 DataSourceType.url 决定*/
    )

    /*================================================*/
    companion object {
        fun newInstance(type: DataSourceType) = HomeMovieListFragment().apply {
            arguments = Bundle().apply { putSerializable(MOVIE_LIST_DATA_TYPE, type) }
        }
    }

}