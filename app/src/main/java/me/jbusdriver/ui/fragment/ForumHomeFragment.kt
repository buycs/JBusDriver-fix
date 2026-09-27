package me.jbusdriver.ui.fragment

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import io.reactivex.disposables.Disposable
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.common.BaseFragment
import me.jbusdriver.base.dpToPx
import me.jbusdriver.base.inflate
import me.jbusdriver.common.toGlideUrlReferedBy
import me.jbusdriver.databinding.FragmentForumHomeBinding
import me.jbusdriver.mvp.bean.FORUM_HOME_URL
import me.jbusdriver.mvp.bean.FORUM_SITE_HOST
import me.jbusdriver.mvp.bean.ForumBoardGroup
import me.jbusdriver.mvp.bean.ForumHotItem
import me.jbusdriver.mvp.bean.ForumHotTab
import me.jbusdriver.mvp.bean.ForumSlide
import me.jbusdriver.mvp.bean.parseForumHome
import me.jbusdriver.ui.activity.ForumThreadActivity
import me.jbusdriver.ui.activity.ForumThreadListActivity
import me.jbusdriver.ui.activity.loadForumPage

/**
 * 論壇首页: 顶部左轮播 + 右热帖(四页签手动切), 下面是分组板块。只读。
 * 抽屉里其他入口都是往 content_main 里 add/hide/show 的 Fragment, 論壇也走同一条路。
 */
class ForumHomeFragment : BaseFragment() {

    private var binding: FragmentForumHomeBinding? = null

    private val slides = ArrayList<ForumSlide>()
    private val hotTabs = ArrayList<ForumHotTab>()

    /** 轮播顶部的序号标签, 下标即 slides 的下标 */
    private val indexViews = ArrayList<TextView>()
    private var currentTab = 0

    /** Fragment 会被 MainActivity 留着复用, 首次进入才自动拉一次; 之后靠下拉刷新 */
    private var loaded = false

    /** 同一页重复加载时先掐掉上一次, 免得旧响应回来把新数据盖掉 */
    private var homeRequest: Disposable? = null

    private val slideAdapter by lazy { SlideAdapter() }
    private val ticker = Handler(Looper.getMainLooper())
    private val turnPage = object : Runnable {
        override fun run() {
            val b = binding ?: return
            // 适配器报的是「轮播次数 x 条数」, 往后翻永远有下一页, 尾→头不用回跳
            if (slides.size > 1) b.vpSlides.currentItem = b.vpSlides.currentItem + 1
            startTicker()
        }
    }

    /**
     * 首页数据是异步回来的, 可见时往往还没到, 所以拿到轮播后要再启一次。
     * isHidden 也要判: 抽屉切走是 hide/show, 翻页动画回来的 onPageSelected 会在隐藏后才到,
     * 不拦住的话轮播会在后台一直翻。
     */
    private fun startTicker() {
        ticker.removeCallbacks(turnPage)
        if (binding == null || isHidden || slides.size <= 1) return
        ticker.postDelayed(turnPage, SLIDE_INTERVAL)
    }

    private fun stopTicker() {
        ticker.removeCallbacks(turnPage)
    }

    /** 换首页数据时重置轮播: 落在中段, 这样往前翻和往后翻都还有足够多的整圈 */
    private fun applySlides(list: List<ForumSlide>) {
        val b = binding ?: return
        slides.clear()
        slides.addAll(list)
        slideAdapter.notifyDataSetChanged()
        renderIndices()
        b.vpSlides.setCurrentItem(slides.size * (LOOP_MULTIPLE / 2), false)
        updateIndexHighlight()
        startTicker()
    }

    /**
     * 轮播顶部的序号标签。数量跟随轮播条数, 点一下直接切过去。
     * 只有一条时不摆 —— 没有「切换」可言。
     */
    private fun renderIndices() {
        val b = binding ?: return
        val bar = b.llSlideIndices
        bar.removeAllViews()
        indexViews.clear()
        if (slides.size <= 1) {
            bar.visibility = View.GONE
            return
        }
        bar.visibility = View.VISIBLE
        val horizontal = viewContext.dpToPx(4f)
        val gap = viewContext.dpToPx(2f)
        slides.indices.forEach { index ->
            val label = TextView(bar.context).apply {
                text = (index + 1).toString()
                gravity = Gravity.CENTER
                maxLines = 1
                setTextSize(TypedValue.COMPLEX_UNIT_SP, SLIDE_INDEX_TEXT_SP)
                setPadding(horizontal, 0, horizontal, 0)
                setOnClickListener { goToSlide(index) }
            }
            bar.addView(
                label,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = gap }
            )
            indexViews.add(label)
        }
        updateIndexHighlight()
    }

    /** ViewPager 是无限循环的, 当前第几条要按 slides.size 取模 */
    private fun updateIndexHighlight() {
        if (indexViews.isEmpty()) return
        val current = (binding?.vpSlides?.currentItem ?: 0) % indexViews.size
        indexViews.forEachIndexed { i, label ->
            val active = i == current
            label.setBackgroundResource(
                if (active) R.drawable.bg_slide_index_active else R.drawable.bg_slide_index
            )
            label.setTextColor(if (active) 0xFFFFFFFF.toInt() else 0xB3FFFFFF.toInt())
            label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /** 以当前所在的那一圈为基准只挪 index 步, 免得从第 1 条点第 2 条要绕回去 */
    private fun goToSlide(index: Int) {
        val b = binding ?: return
        val size = slides.size
        if (size <= 0) return
        val current = b.vpSlides.currentItem
        b.vpSlides.currentItem = current - current % size + index
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentForumHomeBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.vpSlides.adapter = slideAdapter
        b.vpSlides.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            // 手动翻页后把自动播放往后推, 不然刚翻开就被下一条顶走
            override fun onPageSelected(position: Int) {
                updateIndexHighlight()
                startTicker()
            }
        })
        if (!loaded) {
            loaded = true
            loadHome()
        }
        // 站点的热帖列表几分钟就换一批, 进来时的那份快照很快就会过时, 留个手动重拉的口子
        b.srRefresh.setColorSchemeResources(
            R.color.colorPrimary, R.color.colorPrimaryDark, R.color.colorPrimaryLight
        )
        b.srRefresh.setOnRefreshListener { loadHome() }
    }

    private fun loadHome() {
        val b = binding ?: return
        // 下拉刷新时页面上已经有内容, 别再盖一个大转圈; 转圈由 SwipeRefreshLayout 自己负责
        if (!b.srRefresh.isRefreshing) b.pbLoading.visibility = View.VISIBLE
        val keepTab = currentTab
        homeRequest?.dispose()
        homeRequest = loadForumPage(
            rxManager,
            FORUM_HOME_URL,
            { html, base -> parseForumHome(html, base) },
            { home ->
                val cur = binding ?: return@loadForumPage
                cur.pbLoading.visibility = View.GONE
                cur.srRefresh.isRefreshing = false
                applySlides(home.slides)

                hotTabs.clear()
                hotTabs.addAll(home.hotTabs)
                renderTabs()
                if (keepTab > 0) selectTab(keepTab)
                renderGroups(home.groups)

                if (home.groups.isEmpty() && home.slides.isEmpty() && home.hotTabs.isEmpty()) {
                    showError("首頁什麼都沒抓到")
                } else {
                    cur.nsvContent.visibility = View.VISIBLE
                }
            },
            { message ->
                loaded = false
                binding?.let {
                    it.pbLoading.visibility = View.GONE
                    it.srRefresh.isRefreshing = false
                    showError(message)
                }
            }
        )
    }

    override fun onResume() {
        super.onResume()
        startTicker()
    }

    override fun onPause() {
        stopTicker()
        super.onPause()
    }

    /** 抽屉切走是 hide/show, 不走 onPause, 所以轮播得在这里自己停/启 */
    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) stopTicker() else startTicker()
    }

    override fun onDestroyView() {
        stopTicker()
        binding = null
        super.onDestroyView()
    }

    private fun renderTabs() {
        val b = binding ?: return
        val bar = b.llHotTabs
        bar.removeAllViews()
        hotTabs.forEachIndexed { index, tab ->
            val view = TextView(bar.context).apply {
                text = tab.name
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(4, 0, 4, 0)
            }
            // 页签只有热帖栏 1/4 宽, 固定字号在窄屏或系统大字号下必然切字, 让它自己缩到放得下
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                view, TAB_TEXT_MIN_SP, TAB_TEXT_MAX_SP, 1, TypedValue.COMPLEX_UNIT_SP            )
            view.setOnClickListener { selectTab(index) }
            bar.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        currentTab = 0
        selectTab(0)
    }

    private fun selectTab(index: Int) {
        if (hotTabs.isEmpty()) return
        val b = binding ?: return
        currentTab = index.coerceIn(0, hotTabs.lastIndex)
        val bar = b.llHotTabs
        val activeColor = ContextCompat.getColor(requireContext(), R.color.forum_board_tag)
        val idleColor = ContextCompat.getColor(requireContext(), R.color.forum_time)
        for (i in 0 until bar.childCount) {
            val view = bar.getChildAt(i) as TextView
            val active = i == currentTab
            view.setTextColor(if (active) activeColor else idleColor)
            view.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
        renderHotList(hotTabs[currentTab].items)
    }

    private fun renderHotList(items: List<ForumHotItem>) {
        val b = binding ?: return
        val list = b.llHotList
        list.removeAllViews()
        items.forEach { item ->
            val view = viewContext.inflate(R.layout.layout_forum_hot_item, list)
            view.findViewById<TextView>(R.id.tv_forum_hot_title).text = item.title
            view.setOnClickListener { ForumThreadActivity.open(requireContext(), item.link) }
            // 外层是 NestedScrollView, 纵向拖动会被整页抢走; 热帖窗口自己滚得动时先把手势留在本层
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    val scroll = b.svHotList
                    val scrollable = scroll.canScrollVertically(-1) || scroll.canScrollVertically(1)
                    b.nsvContent.requestDisallowInterceptTouchEvent(scrollable)
                }
                false
            }
            list.addView(view)
        }
    }

    private fun renderGroups(groups: List<ForumBoardGroup>) {
        val b = binding ?: return
        val root = b.llGroups
        root.removeAllViews()
        groups.forEach { group ->
            val header = viewContext.inflate(R.layout.layout_forum_group_title, root)
            header.findViewById<TextView>(R.id.tv_forum_group_title).text = group.name
            root.addView(header)

            group.boards.forEach { entry ->
                val row = viewContext.inflate(R.layout.layout_forum_nav_item, root)
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
        binding?.tvError?.apply {
            visibility = View.VISIBLE
            text = msg
        }
    }

    private inner class SlideAdapter : PagerAdapter() {
        // 只有一条时不做循环, 否则一个「无限」页范围会让人以为还能往下翻
        override fun getCount() = if (slides.size > 1) slides.size * LOOP_MULTIPLE else slides.size

        override fun isViewFromObject(view: View, obj: Any) = view === obj

        override fun getItemPosition(obj: Any) = POSITION_NONE

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val slide = slides[position % slides.size]
            val view = LayoutInflater.from(container.context)
                .inflate(R.layout.layout_forum_slide, container, false)
            GlideApp.with(view)
                .load(slide.image.toGlideUrlReferedBy(FORUM_SITE_HOST))
                .error(R.drawable.ic_image_error)
                .into(view.findViewById<ImageView>(R.id.iv_forum_slide))
            view.findViewById<TextView>(R.id.tv_forum_slide_title).text = slide.title
            // 点击挂在透明覆盖层上, 图片和标题条都不必各自处理
            view.findViewById<View>(R.id.v_slide_click).setOnClickListener {
                ForumThreadActivity.open(requireContext(), slide.link)
            }
            container.addView(view)
            return view
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }
    }

    companion object {
        private const val SLIDE_INTERVAL = 4000L
        /** 页签四等分右侧热帖栏的宽度: 宽屏按上限显示, 窄屏或系统大字号往下缩到放得下四个汉字 */
        private const val TAB_TEXT_MAX_SP = 10
        private const val TAB_TEXT_MIN_SP = 7
        /** 轮播范围复制的份数: 取中段起翻, 前后各留一半, 一次会话内翻不到头 */
        private const val LOOP_MULTIPLE = 1000
        /** 顶部序号标签的字号: 轮播只有 44% 屏宽, 号多了要排得下 */
        private const val SLIDE_INDEX_TEXT_SP = 10f

        fun newInstance() = ForumHomeFragment()
    }
}
