package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.jbusdriver.R
import me.jbusdriver.base.toast
import me.jbusdriver.databinding.ActivityForumThreadBinding
import me.jbusdriver.mvp.bean.FORUM_SITE_HOST
import me.jbusdriver.mvp.bean.ForumFloor
import me.jbusdriver.mvp.bean.ForumThreadPost
import me.jbusdriver.mvp.bean.parseForumThread
import me.jbusdriver.ui.adapter.ForumFloorAdapter

/**
 * 帖子正文。只读: 能翻楼层、能看大图, 不回复不发帖。
 *
 * 楼层是连续追加的: 滚到底自动接下一页, 底部分页条是同一件事的按钮版,
 * 所以这里只有「往后」一个方向, 不做跳页。
 */
class ForumThreadActivity : ForumBaseActivity() {

    private lateinit var binding: ActivityForumThreadBinding

    private val url by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }

    private var nextUrl = ""
    private var requesting = false
    private var firstPage = 1
    private var loadedPage = 1
    private var totalPage = 1

    private val floorAdapter by lazy {
        ForumFloorAdapter { floor, index -> showImage(floor, index) }
    }

    private val autoPaging = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return
            val lm = rv.layoutManager as? LinearLayoutManager ?: return
            val count = rv.adapter?.itemCount ?: 0
            if (count > 0 && lm.findLastVisibleItemPosition() >= count - AUTO_AHEAD) {
                loadFloors(nextUrl, append = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForumThreadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fitSystemBars(binding.toolbar, binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.rvFloors.layoutManager = LinearLayoutManager(this)
        floorAdapter.bindToRecyclerView(binding.rvFloors)
        binding.rvFloors.addOnScrollListener(autoPaging)
        binding.tvPageNext.setOnClickListener { loadFloors(nextUrl, append = true) }

        if (url.isBlank()) {
            showError("无效的链接")
            return
        }
        loadFloors(url, append = false)
    }

    private fun loadFloors(requestUrl: String, append: Boolean) {
        if (requestUrl.isBlank() || requesting) return
        requesting = true
        if (!append) binding.pbLoading.visibility = View.VISIBLE
        loadPage(
            requestUrl,
            { html, base -> parseForumThread(html, base) },
            { post ->
                requesting = false
                binding.pbLoading.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                if (post.floors.isEmpty()) {
                    if (append) toast(EMPTY_TIP) else showError(EMPTY_TIP)
                } else if (append) {
                    loadedPage += 1
                    floorAdapter.addData(post.floors)
                } else {
                    firstPage = post.page
                    loadedPage = post.page
                    showThread(post)
                }
                totalPage = maxOf(totalPage, post.totalPage)
                // 追加时也要推进游标, 否则「下一頁」会一直重刷同一页
                nextUrl = post.nextUrl
                renderPagination()
            },
            { message ->
                requesting = false
                binding.pbLoading.visibility = View.GONE
                if (append) toast(message) else showError(message)
                // 失败时游标不动, 分页条保持可点, 再按一次就是重试同一页
                renderPagination()
            }
        )
    }

    private fun renderPagination() {
        val visible = totalPage > 1
        binding.llPagination.visibility = if (visible) View.VISIBLE else View.GONE
        binding.vPaginationDivider.visibility = if (visible) View.VISIBLE else View.GONE
        if (!visible) {
            supportActionBar?.subtitle = null
            return
        }
        supportActionBar?.subtitle = getString(R.string.forum_page_total, totalPage)
        binding.tvPageInfo.text = if (loadedPage <= firstPage) {
            getString(R.string.forum_page_single, firstPage, totalPage)
        } else {
            getString(R.string.forum_page_range, firstPage, loadedPage, totalPage)
        }

        val atEnd = nextUrl.isBlank() || loadedPage >= totalPage
        val dimmed = requesting || atEnd
        val color = ContextCompat.getColor(
            this,
            if (dimmed) R.color.forum_time else R.color.forum_board_tag
        )
        binding.tvPageNext.apply {
            text = getString(
                when {
                    requesting -> R.string.forum_page_loading
                    atEnd -> R.string.forum_page_end
                    else -> R.string.forum_page_next
                }
            )
            isEnabled = !dimmed
            setTextColor(color)
        }
    }


    private fun showThread(post: ForumThreadPost) {
        supportActionBar?.title = post.title.ifBlank { getString(R.string.forum_thread_default) }
        floorAdapter.setNewData(post.floors)
        binding.rvFloors.visibility = View.VISIBLE
    }

    private fun showImage(floor: ForumFloor, index: Int) {
        if (floor.images.isEmpty() || index < 0) return
        WatchLargeImageActivity.startShow(this, floor.images, index, FORUM_SITE_HOST)
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
        private const val AUTO_AHEAD = 3
        private const val EMPTY_TIP = "沒有解析到樓層內容（可能要登錄，或頁面結構變了）"

        fun open(context: Context, link: String) {
            context.startActivity(Intent(context, ForumThreadActivity::class.java).apply {
                putExtra(EXTRA_URL, link)
            })
        }
    }
}
