package me.jbusdriver.ui.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import me.jbusdriver.R
import me.jbusdriver.base.common.BaseActivity

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        immersionBar.transparentBar().init()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        init()
    }

    private fun init() {
        //授权与否都不影响后面的跳转,只是 API 23-28 上没权限时备份目录会回落到私有目录
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            goMain()
        } else {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), Request_Storage)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Request_Storage) goMain()
    }

    private fun goMain() {
        MainActivity.start(this)
        finish()
    }

    companion object {
        private const val Request_Storage = 1

        fun start(context: Context) {
            context.startActivity(Intent(context, SplashActivity::class.java))
        }
    }
}
