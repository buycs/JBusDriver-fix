package me.jbusdriver.base.common

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.view.MenuItem
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.gyf.barlibrary.ImmersionBar
import com.umeng.analytics.MobclickAgent
import io.reactivex.disposables.CompositeDisposable
import me.jbusdriver.base.BuildConfig
import me.jbusdriver.base.KLog

/**
 * Created by Administrator on 2016/8/11 0011.
 * 日志记录及基础方法复用
 */
abstract class BaseActivity : AppCompatActivity() {
    protected val rxManager by lazy { CompositeDisposable() }
    protected val TAG: String by lazy { this::class.java.simpleName }
    private var destroyed = false

    protected val immersionBar by lazy { ImmersionBar.with(this)!! }

    override fun onCreate(savedInstanceState: Bundle?) {
        KLog.t(TAG).d("onCreate $savedInstanceState")
        super.onCreate(savedInstanceState)
    }


    override fun onResume() {
        super.onResume()
        MobclickAgent.onResume(this)
    }


    override fun onPause() {
        super.onPause()
        MobclickAgent.onPause(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        rxManager.clear()
        rxManager.dispose()
        KLog.t(TAG).d("onDestroy")
        immersionBar.destroy()
        destroyed = true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    val isDestroyedCompatible: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= 17)
                isDestroyedCompatible17
            else
                destroyed || super.isFinishing()

        }

    private val isDestroyedCompatible17: Boolean
        @TargetApi(17)
        get() = super.isDestroyed()

    val viewContext: Context by lazy { this }

    /**
     * 自绘标题栏的页面(论坛三页、磁力结果页)系统栏 inset 得自己吃:
     * 顶栏吃 top(标题栏底色能铺进状态栏, 标题不会被时钟压住), 根布局吃 bottom/左右(手势条不压底栏)。
     * 根布局那份 insets 不消费, 否则子 View 就拿不到 top 了。
     */
    protected fun fitSystemBars(toolbar: View, root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, 0, bars.right, bars.bottom)
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }
}
