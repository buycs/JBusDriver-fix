package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.common.toGlideUrlReferedBy
import me.jbusdriver.databinding.ActivityForumBoardBinding
import me.jbusdriver.mvp.bean.FORUM_GUIDE_HOT_URL
import me.jbusdriver.mvp.bean.FORUM_HOME_URL
import me.jbusdriver.mvp.bean.FORUM_SITE_HOST
import me.jbusdriver.mvp.bean.ForumBoardGroup
import me.jbusdriver.mvp.bean.ForumHotItem
import me.jbusdriver.mvp.bean.ForumHotTab
import me.jbusdriver.mvp.bean.ForumSlide
import me.jbusdriver.mvp.bean.forumTidOf
import me.jbusdriver.mvp.bean.parseForumHome
import me.jbusdriver.mvp.bean.parseForumThreadList

/**
 * 論壇首页: 顶部左轮播 + 右热帖(四页签手动切), 下面是分组板块。只读。
 */
class ForumBoardActivity : ForumBaseActivity() {

    private lateinit var binding: ActivityForumBoardBinding

    private val slides = ArrayList<ForumSlide>()
    private val hotTabs = ArrayList<ForumHotTab>()
    private val featured = ArrayList<ForumHotItem>()
    private val loadingTabs = HashSet<Int>()
    private var currentTab = 0

    /** 站点页签里没有「熱門主題」时, 这一栏才需要从導讀页现拉; 记下来才能区分"只有精選内容"和"已经拉完" */
    private var lazyHotTab = -1

    private val slideAdapter by lazy { SlideAdapter() }
    private val ticker = Handler(Looper.getMainLooper())
    private val turnPage = object : Runnable {
        override fun run() {
            if (slides.size > 1) {
                binding.vpSlides.currentItem = (binding.vpSlides.currentItem + 1) % slides.size
            }
            ticker.postDelayed(this, SLIDE_INTERVAL)
        }
    }

    /** 首页数据是异步回来的, onResume 时往往还没到, 所以拿到轮播后要再启一次 */
    private fun startTicker() {
        ticker.removeCallbacks(turnPage)
        if (slides.size > 1) ticker.postDelayed(turnPage, SLIDE_INTERVAL)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForumBoardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSystemBars(binding.toolbar, binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.forum_board_title)

        binding.vpSlides.adapter = slideAdapter
        binding.vpSlides.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            // 手动翻页后把自动播放往后推, 不然刚翻开就被下一条顶走
            override fun onPageSelected(position: Int) {
                ticker.removeCallbacks(turnPage)
                ticker.postDelayed(turnPage, SLIDE_INTERVAL)
            }
        })

        loadPage(
            FORUM_HOME_URL,
            { html, base -> parseForumHome(html, base) },
            { home ->
                binding.pbLoading.visibility = View.GONE
                slides.clear()
                slides.addAll(home.slides)
                slideAdapter.notifyDataSetChanged()
                startTicker()

                hotTabs.clear()
                hotTabs.addAll(home.hotTabs)
                featured.clear()
                featured.addAll(home.featured)
                val hotIndex = hotTabs.indexOfFirst { it.name.contains(HOT_TAB_KEYWORD) }
                if (hotIndex >= 0) {
                    lazyHotTab = -1
                    hotTabs[hotIndex] = hotTabs[hotIndex]
                        .copy(items = mergedWithFeatured(hotTabs[hotIndex].items))
                } else {
                    // 站点页签里没有「熱門主題」, 这一栏由本站自己拼出来, 数据现拉
                    lazyHotTab = 0
                    hotTabs.add(
                        0,
                        ForumHotTab(getString(R.string.forum_hot_tab_hot), mergedWithFeatured(emptyList()))
                    )
                }
                renderTabs()
                renderGroups(home.groups)

                if (home.groups.isEmpty() && home.slides.isEmpty() && home.hotTabs.isEmpty()) {
                    showError("首頁什麼都沒抓到")
                } else {
                    binding.nsvContent.visibility = View.VISIBLE
                }
            },
            { message ->
                binding.pbLoading.visibility = View.GONE
                showError(message)
            }
        )
    }

    override fun onResume() {
        super.onResume()
        startTicker()
    }

    override fun onPause() {
        ticker.removeCallbacks(turnPage)
        super.onPause()
    }

    private fun renderTabs() {
        val bar = binding.llHotTabs
        bar.removeAllViews()
        hotTabs.forEachIndexed { index, tab ->
            val view = TextView(bar.context).apply {
                text = tab.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(4, 0, 4, 0)
            }
            view.setOnClickListener { selectTab(index) }
            bar.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        currentTab = 0
        selectTab(0)
    }

    private fun selectTab(index: Int) {
        if (hotTabs.isEmpty()) return
        currentTab = index.coerceIn(0, hotTabs.lastIndex)
        val bar = binding.llHotTabs
        val activeColor = ContextCompat.getColor(this, R.color.forum_board_tag)
        val idleColor = ContextCompat.getColor(this, R.color.forum_time)
        for (i in 0 until bar.childCount) {
            val view = bar.getChildAt(i) as TextView
            val active = i == currentTab
            view.setTextColor(if (active) activeColor else idleColor)
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
        val tab = hotTabs[currentTab]
        if (currentTab == lazyHotTab && loadingTabs.add(currentTab)) {
            loadHotTab(currentTab)
        }
        renderHotList(tab.items)
    }

    /** 精選內容并到「熱門主題」开头: 站方人工挑的那几条排在前面, 同帖(tid 相同)不重复出现 */
    private fun mergedWithFeatured(items: List<ForumHotItem>): List<ForumHotItem> =
        (featured + items).distinctBy { forumTidOf(it.link) }

    /** 站點首頁只内嵌了三個頁簽時, 「熱門主題」這一栏改从導讀頁現拉 */
    private fun loadHotTab(index: Int) {
        loadPage(
            FORUM_GUIDE_HOT_URL,
            { html, base -> parseForumThreadList(html, base) },
            { page ->
                loadingTabs.remove(index)
                val items = mergedWithFeatured(
                    page.threads.take(HOT_LIMIT).map { ForumHotItem(it.title, it.url) }
                )
                hotTabs[index] = hotTabs[index].copy(items = items)
                if (currentTab == index) renderHotList(items)
            },
            { message ->
                loadingTabs.remove(index)
                toast(message)
            }
        )
    }

    private fun renderHotList(items: List<ForumHotItem>) {
        val list = binding.llHotList
        list.removeAllViews()
        items.forEach { item ->
            val view = inflate(R.layout.layout_forum_hot_item, list)
            view.findViewById<TextView>(R.id.tv_forum_hot_title).text = item.title
            view.setOnClickListener { ForumThreadActivity.open(this, item.link) }
            // 外层是 NestedScrollView, 纵向拖动会被整页抢走; 热帖窗口自己滚得动时先把手势留在本层
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val scroll = binding.svHotList
                    val scrollable = scroll.canScrollVertically(-1) || scroll.canScrollVertically(1)
                    binding.nsvContent.requestDisallowInterceptTouchEvent(scrollable)
                }
                false
            }
            list.addView(view)
        }
    }

    private fun renderGroups(groups: List<ForumBoardGroup>) {
        val root = binding.llGroups
        root.removeAllViews()
        groups.forEach { group ->
            val title = inflate(R.layout.layout_forum_group_title, root) as TextView
            title.text = group.name
            root.addView(title)

            group.boards.forEach { entry ->
                val row = inflate(R.layout.layout_forum_nav_item, root)
                row.findViewById<TextView>(R.id.tv_forum_nav_name).text = entry.name
                val desc = row.findViewById<TextView>(R.id.tv_forum_nav_desc)
                desc.text = entry.desc
                desc.visibility = if (entry.desc.isBlank()) View.GONE else View.VISIBLE
                row.setOnClickListener {
                    ForumThreadListActivity.open(it.context, entry.url, entry.name)
                }
                root.addView(row)
            }
        }
    }

    private fun showError(msg: String) {
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = msg
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private inner class SlideAdapter : PagerAdapter() {
        override fun getCount() = slides.size

        override fun isViewFromObject(view: View, obj: Any) = view === obj

        override fun getItemPosition(obj: Any) = POSITION_NONE

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val slide = slides[position]
            val view = layoutInflater.inflate(R.layout.layout_forum_slide, container, false)
            GlideApp.with(view)
                .load(slide.image.toGlideUrlReferedBy(FORUM_SITE_HOST))
                .error(R.drawable.ic_image_error)
                .into(view.findViewById<ImageView>(R.id.iv_forum_slide))
            view.findViewById<TextView>(R.id.tv_forum_slide_title).text = slide.title
            view.setOnClickListener { ForumThreadActivity.open(this@ForumBoardActivity, slide.link) }
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }
    }

    companion object {
        private const val SLIDE_INTERVAL = 4000L
        /** 窗口固定露出五条, 多拉的这几条靠上下滑动看到 */
        private const val HOT_LIMIT = 10

        /** 「最新熱門」这类页签名里带門字(熱點話題不带), 缺了才去導讀补拉 */
        private const val HOT_TAB_KEYWORD = "門"

        fun open(context: Context) {
            context.startActivity(Intent(context, ForumBoardActivity::class.java))
        }
    }
}
