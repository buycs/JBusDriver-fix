package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.databinding.ActivityForumThreadListBinding
import me.jbusdriver.mvp.bean.ForumHotItem
import me.jbusdriver.mvp.bean.ForumHotTab
import me.jbusdriver.mvp.bean.ForumOption
import me.jbusdriver.mvp.bean.ForumThreadList
import me.jbusdriver.mvp.bean.ForumThreadSummary
import me.jbusdriver.mvp.bean.parseForumThreadList
import me.jbusdriver.ui.adapter.ForumThreadAdapter

/**
 * 板塊 / 導讀下的帖子列表。只读, 底部「下一页」续拉。
 * 顶部三块都取自站点列表页自己内嵌的 HTML: 信息展示区(三栏手动切)、主题分类行、排序行。
 */
class ForumThreadListActivity : ForumBaseActivity() {

    private lateinit var binding: ActivityForumThreadListBinding

    private val url by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val boardName by lazy { intent.getStringExtra(EXTRA_NAME).orEmpty() }

    /** 点了分类/排序后当前页就不是进来的那个地址了, 下拉刷新要重拉这个 */
    private var currentUrl = ""
    private var nextUrl = ""
    private var requesting = false

    private val hotTabs = ArrayList<ForumHotTab>()
    private var currentTab = 0

    /** 分类/排序条目的左右内边距: 布局里写 dp, 代码里要的是 px */
    private val optionPaddingPx by lazy {
        (OPTION_PADDING_DP * resources.displayMetrics.density).toInt()
    }

    private val threadAdapter by lazy { ForumThreadAdapter() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForumThreadListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSystemBars(binding.toolbar, binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = boardName.ifBlank { getString(R.string.forum_board_title) }

        currentUrl = url
        binding.rvThreads.layoutManager = LinearLayoutManager(this)
        threadAdapter.bindToRecyclerView(binding.rvThreads)
        threadAdapter.setEnableLoadMore(true)
        threadAdapter.setOnLoadMoreListener({ loadThreads(nextUrl, append = true) }, binding.rvThreads)
        threadAdapter.setOnItemClickListener { _, view, position ->
            threadAdapter.data.getOrNull(position)?.let {
                ForumThreadActivity.open(view.context, it.url)
            }
        }

        if (url.isBlank()) {
            showError("無效的鏈接")
            return
        }
        binding.srRefresh.setColorSchemeResources(
            R.color.colorPrimary, R.color.colorPrimaryDark, R.color.colorPrimaryLight
        )
        binding.srRefresh.setOnRefreshListener { loadThreads(currentUrl, append = false) }
        loadThreads(url, append = false)
    }

    private fun loadThreads(requestUrl: String, append: Boolean) {
        if (requestUrl.isBlank() || requesting) {
            // 请求在飞的时候下拉会被吞掉, 但转圈得停掉, 不然一直挂在顶上
            if (!append) binding.srRefresh.isRefreshing = false
            return
        }
        requesting = true
        // 下拉刷新时列表还在, 不用再盖一个大转圈
        if (!append && !binding.srRefresh.isRefreshing) binding.pbLoading.visibility = View.VISIBLE
        loadPage(
            requestUrl,
            { html, base -> parseForumThreadList(html, base) },
            { page ->
                requesting = false
                binding.pbLoading.visibility = View.GONE
                binding.srRefresh.isRefreshing = false
                binding.tvError.visibility = View.GONE
                if (append) {
                    threadAdapter.addData(page.threads)
                    threadAdapter.loadMoreComplete()
                } else {
                    currentUrl = requestUrl
                    showThreads(page.title, page.threads)
                    showHeader(page)
                }
                nextUrl = page.nextUrl
                supportActionBar?.subtitle = page.pageText
                if (page.threads.isEmpty()) {
                    threadAdapter.loadMoreEnd()
                    if (!append) showError("沒有帖子")
                } else if (!page.hasNext) {
                    threadAdapter.loadMoreEnd()
                }
            },
            { message ->
                requesting = false
                binding.pbLoading.visibility = View.GONE
                binding.srRefresh.isRefreshing = false
                if (append) {
                    threadAdapter.loadMoreComplete()
                    toast(message)
                } else {
                    showError(message)
                }
            }
        )
    }

    private fun showThreads(title: String, threads: List<ForumThreadSummary>) {
        if (boardName.isBlank() && title.isNotBlank()) supportActionBar?.title = title
        // 整页重填前先解掉「已到底」: 不然刷新后下一页再也拉不出来
        threadAdapter.loadMoreComplete()
        threadAdapter.setNewData(threads)
        binding.rvThreads.visibility = View.VISIBLE
    }

    private fun showHeader(page: ForumThreadList) {
        hotTabs.clear()
        hotTabs.addAll(page.hotTabs)
        if (hotTabs.isEmpty()) {
            binding.llBoardHot.visibility = View.GONE
        } else {
            binding.llBoardHot.visibility = View.VISIBLE
            renderTabs()
        }
        showOptionRow(binding.hsvBoardFilters, binding.llBoardFilters, page.filterOptions)
        showOptionRow(binding.hsvBoardSorts, binding.llBoardSorts, page.sortOptions)
        val hasFilter = binding.hsvBoardFilters.visibility == View.VISIBLE
        val hasSort = binding.hsvBoardSorts.visibility == View.VISIBLE
        // 只剩一行的话, 中间那条分隔线和整块白框都得跟着收, 不然顶上多出一道空带
        binding.vBoardDivider.visibility = if (hasFilter && hasSort) View.VISIBLE else View.GONE
        binding.llBoardBars.visibility = if (hasFilter || hasSort) View.VISIBLE else View.GONE
    }

    /** 站点给不同版块的筛选/排序行不是一定的, 抓不到选项就整行不占位 */
    private fun showOptionRow(row: View, bar: LinearLayout, options: List<ForumOption>) {
        renderOptions(bar, options)
        row.visibility = if (options.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderTabs() {
        val bar = binding.llBoardTabs
        bar.removeAllViews()
        hotTabs.forEachIndexed { index, tab ->
            val view = TextView(bar.context).apply {
                text = tab.name
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(4, 0, 4, 0)
                setOnClickListener { selectTab(index) }
            }
            bar.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        // 换排序/分类会整块重画, 下拉刷新也一样, 停在用户原来那一栏别跳回第一栏
        selectTab(currentTab)
    }

    private fun selectTab(index: Int) {
        if (hotTabs.isEmpty()) return
        currentTab = index.coerceIn(0, hotTabs.lastIndex)
        val bar = binding.llBoardTabs
        for (i in 0 until bar.childCount) {
            paintOption(bar.getChildAt(i) as TextView, i == currentTab)
        }
        renderHotList(hotTabs[currentTab].items)
    }

    private fun renderHotList(items: List<ForumHotItem>) {
        val list = binding.llBoardList
        list.removeAllViews()
        items.forEach { item ->
            val view = inflate(R.layout.layout_forum_hot_item, list)
            view.findViewById<TextView>(R.id.tv_forum_hot_title).text = item.title
            view.setOnClickListener { ForumThreadActivity.open(this, item.link) }
            list.addView(view)
        }
    }

    private fun renderOptions(bar: LinearLayout, options: List<ForumOption>) {
        bar.removeAllViews()
        options.forEach { option ->
            val view = TextView(bar.context).apply {
                text = option.label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                maxLines = 1
                setPadding(optionPaddingPx, 0, optionPaddingPx, 0)
                setOnClickListener { openOption(option) }
            }
            paintOption(view, option.selected)
            bar.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun paintOption(view: TextView, selected: Boolean) {
        val activeColor = ContextCompat.getColor(this, R.color.forum_board_tag)
        val idleColor = ContextCompat.getColor(this, R.color.forum_time)
        view.setTextColor(if (selected) activeColor else idleColor)
        view.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    /** 分类和排序都是换地址重拉一页; 点了当前项就不重复请求了 */
    private fun openOption(option: ForumOption) {
        if (option.url.isBlank() || option.url == currentUrl) return
        binding.rvThreads.scrollToPosition(0)
        loadThreads(option.url, append = false)
    }

    private fun showError(msg: String) {
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = msg
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val OPTION_PADDING_DP = 10

        fun open(context: Context, link: String, name: String = "") {
            context.startActivity(Intent(context, ForumThreadListActivity::class.java).apply {
                putExtra(EXTRA_URL, link)
                putExtra(EXTRA_NAME, name)
            })
        }
    }
}
