package me.jbusdriver.component.magnet.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.component.magnet.R
import me.jbusdriver.component.magnet.databinding.CompMagnetActivityMagnetListBinding
import me.jbusdriver.component.magnet.ui.fragment.JAVBusOfficialLoaderKey
import me.jbusdriver.component.magnet.ui.fragment.MagnetListFragment

class MagnetPagerListActivity : BaseActivity() {

    private lateinit var binding: CompMagnetActivityMagnetListBinding

    private val keyword by lazy {
        intent.getStringExtra(C.BundleKey.Key_1) ?: error("must set keyword")
    }
    private val link by lazy { intent.getStringExtra(C.BundleKey.Key_2).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = CompMagnetActivityMagnetListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSystemBars(binding.compMagnetToolbar, binding.root)
        setSupportActionBar(binding.compMagnetToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setTitle(keyword)
        // 只保留站方官方磁力源这一个结果页: 第三方源要在设置里勾选才会出现, 且多数已失效
        supportFragmentManager.beginTransaction()
            .replace(R.id.comp_magnet_fl_magnet_list, MagnetListFragment.newInstance(link, JAVBusOfficialLoaderKey))
            .commit()

    }

    private fun setTitle(title: String) {
        supportActionBar?.title = "$title 的磁力链接"
    }

    companion object {
        fun start(context: Context, keyword: String, link: String) {
            context.startActivity(Intent(context, MagnetPagerListActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, keyword)
                putExtra(C.BundleKey.Key_2, link)
            })
        }
    }
}
