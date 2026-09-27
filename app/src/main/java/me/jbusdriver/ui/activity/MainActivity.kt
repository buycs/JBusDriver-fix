package me.jbusdriver.ui.activity

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.google.android.material.navigation.NavigationView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import kotlin.math.abs
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.AppBaseActivity
import me.jbusdriver.base.ui.TabOverflowHost
import me.jbusdriver.common.JBus
import me.jbusdriver.mvp.MainContract
import me.jbusdriver.mvp.bean.*
import me.jbusdriver.mvp.presenter.MainPresenterImpl
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.BottomTabs
import me.jbusdriver.ui.widget.BottomTabBar

class MainActivity : AppBaseActivity<MainContract.MainPresenter, MainContract.MainView>(),
    NavigationView.OnNavigationItemSelectedListener, MainContract.MainView, TabOverflowHost {

    private val navigationView by lazy { findViewById<NavigationView>(R.id.nav_view) }
    private val bottomNav by lazy { findViewById<BottomTabBar>(R.id.bottom_nav) }
    private val contentMain by lazy { findViewById<View>(R.id.content_main) }
    private val appBar by lazy { findViewById<View>(R.id.appbar) }
    private val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
    /** 当前落在 content_main 里的那个页签/菜单项 id, 两套外壳共用*/
    private var selectedId = 0
    private var drawerToggle: ActionBarDrawerToggle? = null
    private var bottomMode = false

    override val isBottomShell: Boolean get() = bottomMode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) intent.putExtras(savedInstanceState)
        // 「最近任务隐藏」: 开就隐藏、关就恢复。API 30+ 立即生效, 更低版本要重启(见 RecentsControl)
        applyRecentsExclusion(AppConfiguration.hideRecent)
        initNavigationView()
        initBottomNav()
        applyShell()
        setNavSelected()
    }


    private fun initNavigationView() {
        setSupportActionBar(toolbar)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        drawerToggle = toggle


        navigationView?.getHeaderView(0)?.apply {
            findViewById<TextView>(R.id.tv_app_version).text = packageInfo?.versionName ?: "未知版本"
            findViewById<LinearLayout>(R.id.ll_git_url).setOnClickListener {
                browse("https://github.com/Ccixyj/JBusDriver")
            }
            findViewById<LinearLayout>(R.id.ll_click_reload).setOnClickListener {
                CacheLoader.lru.evictAll()
                CacheLoader.acache.clear()
                JBus.JBusServices.clear()
                SplashActivity.start(this@MainActivity)
                finish()
            }

            findViewById<TextView>(R.id.tv_app_setting).setOnClickListener {
                SettingActivity.start(this@MainActivity)
                drawer.closeDrawer(GravityCompat.START)
            }


            fun tintTextLeftDrawable(parent: ViewGroup) {

                (0..parent.childCount).forEachIndexed { i, _ ->
                    //如果是容器,直接查子view
                    (parent.getChildAt(i) as? ViewGroup)?.let {
                        Schedulers.trampoline().scheduleDirect {
                            tintTextLeftDrawable(it)
                        }

                    } ?: (parent.getChildAt(i) as? TextView)?.compoundDrawables?.forEach {
                        if (it != null)
                            DrawableCompat.setTint(it, R.color.colorAccent.toColorInt())
                    }
                }
            }
            if (Build.VERSION.SDK_INT < 23 && this as? ViewGroup != null) {
                Schedulers.single().scheduleDirect {
                    Schedulers.trampoline().scheduleDirect {
                        tintTextLeftDrawable(this)
                    }.addTo(rxManager)
                }
            }


        }
        navigationView.setNavigationItemSelectedListener(this)

    }

    private fun initBottomNav() {
        bottomNav.setTabs(BottomTabs.ALL)
        bottomNav.onTabSelected = { id -> showTab(id) }
        /*
         * DrawerLayout 会把 WindowInsets 吃掉再按子 View 的 fitsSystemWindows 分发,
         * 底部栏这层拿不到, 所以从 android.R.id.content 上取导航栏高度补给自己。
         * 圆角屏 / 手势导航下系统给的 bottom 可能很小甚至为 0, 最后一行字会被屏幕圆角啃掉,
         * 所以给它一个下限; 系统本来就给够的设备不额外加高。
         */
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomNav.updatePadding(bottom = maxOf(bars.bottom, dp(BOTTOM_BAR_MIN_INSET)))
            insets
        }
        // 底部栏高度(含刚补的 inset)定了才能给内容区留出同样的空间
        bottomNav.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncContentInset() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 切到底部页签: 先刷高亮再换内容 */
    private fun showTab(id: Int) {
        val tab = BottomTabs.byId(id) ?: return
        bottomNav.selectTab(id)
        switchFragment(id)
        supportActionBar?.title = tab.label
        applyTopBar()
    }

    /**
     * 底部样式下顶栏整条收起: 页面级动作(跳页/全部影片/修改收藏目录/收藏)都进了标签页最右侧的三点菜单,
     * 影片页和女优页的顶栏原本只剩一个搜索入口, 而底部页签里已经有独立的搜索页, 所以不再保留;
     * 抽屉样式下顶栏一直在。
     */
    private fun applyTopBar() {
        setTopBarVisible(!bottomMode)
    }

    /**
     * 收顶栏靠把 AppBarLayout 的高度压成 0, 不是切 visibility。
     * content_main 挂着 appbar_scrolling_view_behavior: AppBarLayout 一旦 GONE,
     * CoordinatorLayout 就不再给 content_main 派发依赖更新, 它的 offset 会冻在上一次的位置,
     * 顶栏那块空白照样留在页面上。高度改 0 时依赖链不断, content_main 正常跟着上移。
     */
    private fun setTopBarVisible(show: Boolean) {
        val lp = appBar.layoutParams ?: return
        val target = if (show) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        if (lp.height != target) {
            lp.height = target
            appBar.layoutParams = lp
        }
        toolbar.visibility = if (show) View.VISIBLE else View.GONE
    }

    //region 内容区下半屏横向滑动切底部页签
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private var swipeDownX = 0f
    private var swipeDownY = 0f
    /** DOWN 落在内容区下半屏才跟踪 */
    private var swipeTracking = false
    /** 已判定为横向滑动, 手势从子 View 手里收走了 */
    private var swipeClaimed = false
    private var swipeSwitched = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!bottomMode) return super.dispatchTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeTracking = ev.rawY >= contentMidY()
                swipeClaimed = false
                swipeSwitched = false
                swipeDownX = ev.rawX
                swipeDownY = ev.rawY
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                swipeTracking = false
                swipeClaimed = false
            }
        }
        if (swipeClaimed) {
            if (ev.actionMasked == MotionEvent.ACTION_MOVE) swipeToTab(ev)
            return true
        }
        if (swipeTracking && ev.actionMasked == MotionEvent.ACTION_MOVE && isHorizontalSwipe(ev)) {
            swipeClaimed = true
            // 顶部子页签的 pager 和列表还在跟同一个手势, 先补一个 CANCEL 把它收回来
            MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }.let { cancel ->
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isHorizontalSwipe(ev: MotionEvent): Boolean {
        val dx = ev.rawX - swipeDownX
        val dy = ev.rawY - swipeDownY
        return abs(dx) > touchSlop && abs(dx) > abs(dy) * 2
    }

    /** 拖过内容区宽度的五分之一换一页, 一次手势最多换一次 */
    private fun swipeToTab(ev: MotionEvent) {
        if (swipeSwitched) return
        val dx = ev.rawX - swipeDownX
        if (abs(dx) < contentMain.width / 5f) return
        val tabs = BottomTabs.ALL
        val current = tabs.indexOfFirst { it.id == selectedId }
        val next = if (dx < 0) current + 1 else current - 1
        if (current < 0 || next !in tabs.indices) return
        swipeSwitched = true
        showTab(tabs[next].id)
    }

    /** 内容区在屏幕上的中线, 底部栏占的那段不算进内容高度 */
    private fun contentMidY(): Float {
        val loc = IntArray(2)
        contentMain.getLocationOnScreen(loc)
        return loc[1] + (contentMain.height - contentMain.paddingBottom) / 2f
    }
    //endregion

    /** 外壳样式在启动时定死, 切换样式走重启, 这里只负责按样式摆两套壳 */
    private fun applyShell() {
        bottomMode = AppConfiguration.uiMode == AppConfiguration.UiMode.Bottom
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.setDrawerLockMode(
            if (bottomMode) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED
        )
        drawerToggle?.isDrawerIndicatorEnabled = !bottomMode
        bottomNav.visibility = if (bottomMode) View.VISIBLE else View.GONE
        // 具体某个页签要不要顶栏由 applyTopBar 决定, 这里先按外壳给个初值
        setTopBarVisible(!bottomMode)
        syncContentInset()
    }

    private fun syncContentInset() {
        val pad = if (bottomMode && bottomNav.visibility == View.VISIBLE) bottomNav.height else 0
        if (contentMain.paddingBottom != pad) contentMain.updatePadding(bottom = pad)
    }


    private fun setNavSelected() {
        if (bottomMode) {
            val menuId = intent.getIntExtra("MenuSelectedItemId", homeTabId())
            showTab(if (bottomNav.hasTab(menuId)) menuId else BottomTabs.MOVIE.id)
            return
        }
        val id = (MenuOp.Ops - MenuOp.mine).firstOrNull()?.id
            ?: MenuOp.Ops.firstOrNull()?.id ?: return
        val menuId = intent.getIntExtra("MenuSelectedItemId", id)
        // 两套外壳的页签 id 不完全重合(底部那组在抽屉 menu 里没有, 如 bottom_setting),
        // 切换外壳后恢复实例会找不到项, 落不回抽屉项就退回默认页而不是留空白
        val select = navigationView.menu.findItem(menuId) ?: navigationView.menu.findItem(id)
        navigationView.setCheckedItem(select?.itemId ?: id)
        select?.let { onNavigationItemSelected(it) }
    }

    /** 「首页设置」存的是底部页签 id, 落不回可选列表就退回影片 */
    private fun homeTabId(): Int =
        BottomTabs.SELECTABLE.firstOrNull { it.id == AppConfiguration.homePageId }?.id
            ?: BottomTabs.MOVIE.id


    override fun onBackPressed() {
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }


    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        switchFragment(item.itemId)
        val drawer = findViewById<DrawerLayout>(R.id.drawer_layout)
        drawer.closeDrawer(GravityCompat.START)
        supportActionBar?.title = item.title
        applyTopBar()
        return true
    }

    private fun switchFragment(itemId: Int) {
        val ft = supportFragmentManager.beginTransaction()

        val replace = supportFragmentManager.findFragmentByTag(itemId.toString()) ?: let {
            MenuOp.byId(itemId)?.initializer?.invoke()?.apply {
                ft.add(R.id.content_main, this, itemId.toString())
            } ?: error("no matched fragment")
        }
        //切换外壳后恢复实例会带进另一套外壳的页, 所以只留目标页, 其余全藏
        for (other in supportFragmentManager.fragments) {
            if (other !== replace) ft.hide(other)
        }
        ft.show(replace)
        ft.commitAllowingStateLoss()
        selectedId = itemId
        /*
         * 顶栏的动作菜单是按「当前可见的那个页」拼的, 而换页走的是 hide/show, 不会自己重建菜单;
         * 不刷一次的话切到收藏页还挂着影片页的搜索图标。
         */
        invalidateOptionsMenu()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (selectedId != 0) {
            outState.putInt("MenuSelectedItemId", selectedId)
        }
        super.onSaveInstanceState(outState)
    }

    override fun createPresenter() = MainPresenterImpl()

    override val layoutId = R.layout.activity_main

    companion object {
        /** 圆角屏 / 手势导航下系统给的底部 inset 可能小到 0, 底栏至少留这么多, 否则最后一行字会被圆角啃掉 */
        private const val BOTTOM_BAR_MIN_INSET = 16

        fun start(current: Activity) {
            current.startActivity(Intent(current, MainActivity::class.java))
        }
    }
}
