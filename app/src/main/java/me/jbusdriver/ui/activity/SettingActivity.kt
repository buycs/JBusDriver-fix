package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import me.jbusdriver.R
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.databinding.ActivitySettingBinding
import me.jbusdriver.ui.fragment.SettingFragment

/**
 * 抽屉样式下的设置页外壳: 只负责工具栏, 内容全在 SettingFragment。
 * 底部样式时同一份内容直接作为「设置」页签, 不必再走这个 Activity。
 */
class SettingActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.setting_container, SettingFragment.newInstance())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        fun start(context: Context) =
            context.startActivity(Intent(context, SettingActivity::class.java))
    }
}
