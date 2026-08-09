package me.jbusdriver.component.magnet.ui.fragment

import android.support.v4.app.Fragment
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.base.common.C
import me.jbusdriver.base.ui.fragment.TabViewPagerFragment
import me.jbusdriver.component.magnet.MagnetPluginHelper
import me.jbusdriver.component.magnet.mvp.MagnetPagerContract.MagnetPagerPresenter
import me.jbusdriver.component.magnet.mvp.MagnetPagerContract.MagnetPagerView
import me.jbusdriver.component.magnet.mvp.presenter.MagnetPagerPresenterImpl
import me.jbusdriver.component.magnet.ui.config.Configuration

/**
 * Created by Administrator on 2017/7/17 0017.
 */
class MagnetPagersFragment : TabViewPagerFragment<MagnetPagerPresenter, MagnetPagerView>(),
    MagnetPagerView {

    private val keyword by lazy {
        arguments?.getString(C.BundleKey.Key_1) ?: error("must set keyword")
    }
    private val link by lazy { arguments?.getString(C.BundleKey.Key_2).orEmpty() }

    override fun createPresenter() = MagnetPagerPresenterImpl()

    override val mTitles: List<String> by lazy {
        val allKeys = MagnetPluginHelper.getLoaderKeys()
        val configKeys = Configuration.getConfigKeys().filter { allKeys.contains(it) }.toMutableList().apply {
            if (this.isEmpty()) {
                this.addAll(Configuration.getConfigKeys())
            }
            Schedulers.single().scheduleDirect {
                Configuration.saveMagnetKeys(this)
            }
        }
        mutableListOf<String>(JAVBusOfficialLoaderKey).apply {
            addAll(configKeys)
        }

    }

    override val mFragments: List<Fragment> by lazy {
        mTitles.map {
            val mapKey = when (it) {
                "default", JAVBusOfficialLoaderKey -> link
                else -> keyword
            }
            MagnetListFragment.newInstance(mapKey, it)
        }
    }


}