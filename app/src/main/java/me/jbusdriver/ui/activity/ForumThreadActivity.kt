package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.Toolbar
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_forum_thread.*
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.toast
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.http.JAVBusService
import me.jbusdriver.mvp.bean.ForumFloor
import me.jbusdriver.mvp.bean.ForumSegment
import me.jbusdriver.mvp.bean.ForumThreadPost
import me.jbusdriver.mvp.bean.parseForumThread
import org.jsoup.Jsoup

class ForumThreadActivity : BaseActivity() {

    private val url by lazy { intent.getStringExtra(EXTRA_URL).orEmpty() }

    private var nextUrl: String = ""
    private var disposed: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forum_thread)
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        btn_load_more.setOnClickListener { loadMore() }
        loadFirstPage()
    }

    private fun loadFirstPage() {
        if (url.isBlank()) {
            showError("无效的链接")
            return
        }
        pb_loading.visibility = View.VISIBLE
        fetchAndParse(url) { post ->
            showThread(post)
        }
    }

    private fun loadMore() {
        if (nextUrl.isBlank()) return
        btn_load_more.isEnabled = false
        fetchAndParse(nextUrl) { post ->
            btn_load_more.isEnabled = true
            addFloors(post)
        }
    }

    private fun fetchAndParse(requestUrl: String, onSuccess: (ForumThreadPost) -> Unit) {
        disposed?.dispose()
        disposed = Flowable.fromCallable {
            val page = JAVBusService.INSTANCE.get(requestUrl).blockingFirst()
            parseForumThread(Jsoup.parse(page))
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { onSuccess(it) },
                { showError(it.message ?: "加载失败", keepContent = true) }
            )
    }

    private fun showThread(post: ForumThreadPost) {
        tv_error.visibility = View.GONE
        pb_loading.visibility = View.GONE
        tv_thread_title.text = post.title.ifBlank { "論壇熱帖" }
        ll_floors.removeAllViews()
        post.floors.forEach { floor ->
            ll_floors.addView(buildFloorView(floor))
        }
        updateLoadMore(post)
        nsv_content.visibility = View.VISIBLE
    }

    private fun addFloors(post: ForumThreadPost) {
        tv_error.visibility = View.GONE
        post.floors.forEach { floor ->
            ll_floors.addView(buildFloorView(floor))
        }
        updateLoadMore(post)
    }

    private fun updateLoadMore(post: ForumThreadPost) {
        nextUrl = post.nextUrl
        btn_load_more.visibility = if (post.hasNext) View.VISIBLE else View.GONE
    }

    private fun buildFloorView(floor: ForumFloor): View {
        val view = layoutInflater.inflate(R.layout.layout_forum_floor, ll_floors, false)
        view.findViewById<TextView>(R.id.tv_floor_no).text = floor.floorNo
        view.findViewById<TextView>(R.id.tv_floor_author).text = floor.author
        view.findViewById<TextView>(R.id.tv_floor_time).text = floor.time
        val content = view.findViewById<LinearLayout>(R.id.ll_floor_content)
        val width = resources.displayMetrics.widthPixels - dp2px(48f)
        floor.segments.forEach { segment ->
            when (segment) {
                is ForumSegment.Text -> {
                    val tv = TextView(this)
                    tv.text = segment.content
                    tv.textSize = 15f
                    tv.setLineSpacing(0f, 1.2f)
                    tv.setTextIsSelectable(true)
                    val lp = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dp2px(6f)
                    content.addView(tv, lp)
                }
                is ForumSegment.Image -> {
                    val iv = ImageView(this)
                    iv.layoutParams = LinearLayout.LayoutParams(width, (width * 0.56f).toInt())
                    iv.scaleType = ImageView.ScaleType.FIT_CENTER
                    content.addView(iv)
                    GlideApp.with(this)
                        .load(segment.url.toGlideNoHostUrl)
                        .into(iv)
                }
            }
        }
        return view
    }

    private fun showError(msg: String, keepContent: Boolean = false) {
        pb_loading.visibility = View.GONE
        if (keepContent && ll_floors.childCount > 0) {
            toast(msg)
            btn_load_more.isEnabled = true
            return
        }
        tv_error.visibility = View.VISIBLE
        tv_error.text = msg
    }

    private fun dp2px(dp: Float) = (dp * resources.displayMetrics.density).toInt()

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        disposed?.dispose()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"

        fun open(context: Context, link: String) {
            context.startActivity(Intent(context, ForumThreadActivity::class.java).apply {
                putExtra(EXTRA_URL, link)
            })
        }
    }
}