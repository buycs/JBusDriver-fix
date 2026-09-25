package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import me.jbusdriver.R
import me.jbusdriver.base.toast
import me.jbusdriver.databinding.ActivityForumThreadListBinding
import me.jbusdriver.mvp.bean.ForumThreadSummary
import me.jbusdriver.mvp.bean.parseForumThreadList
import me.jbusdriver.ui.adapter.ForumThreadAdapter

/**
 * 板塊 / 導讀下的帖子列表。只读, 底部「下一页」续拉。
 */
class ForumThreadListActivity : ForumBaseActivity() {

    private lateinit var binding: ActivityForumThreadListBinding

    private val url by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }
    private val boardName by lazy { intent.getStringExtra(EXTRA_NAME).orEmpty() }

    private var nextUrl = ""
    private var requesting = false

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
        loadThreads(url, append = false)
    }

    private fun loadThreads(requestUrl: String, append: Boolean) {
        if (requestUrl.isBlank() || requesting) return
        requesting = true
        if (!append) binding.pbLoading.visibility = View.VISIBLE
        loadPage(
            requestUrl,
            { html, base -> parseForumThreadList(html, base) },
            { page ->
                requesting = false
                binding.pbLoading.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                if (append) {
                    threadAdapter.addData(page.threads)
                    threadAdapter.loadMoreComplete()
                } else {
                    showThreads(page.title, page.threads)
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
        threadAdapter.setNewData(threads)
        binding.rvThreads.visibility = View.VISIBLE
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

        fun open(context: Context, link: String, name: String = "") {
            context.startActivity(Intent(context, ForumThreadListActivity::class.java).apply {
                putExtra(EXTRA_URL, link)
                putExtra(EXTRA_NAME, name)
            })
        }
    }
}
