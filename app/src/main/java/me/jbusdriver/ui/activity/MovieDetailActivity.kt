package me.jbusdriver.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.content.res.ResourcesCompat
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.target.DrawableImageViewTarget
import android.view.ViewGroup
import com.gyf.barlibrary.ImmersionBar
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.common.AppBaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.base.inflate
import me.jbusdriver.base.urlPath
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.component.magnet.ui.activity.MagnetPagerListActivity
import me.jbusdriver.mvp.MovieDetailContract
import me.jbusdriver.mvp.bean.Movie
import me.jbusdriver.mvp.bean.MovieDetail
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.mvp.presenter.MovieDetailPresenterImpl
import me.jbusdriver.ui.holder.*


class MovieDetailActivity :
    AppBaseActivity<MovieDetailContract.MovieDetailPresenter, MovieDetailContract.MovieDetailView>(),
    MovieDetailContract.MovieDetailView {

    private val statusBarHeight by lazy { ImmersionBar.getActionBarHeight(this) }

    private lateinit var collectMenu: MenuItem
    private lateinit var removeCollectMenu: MenuItem

    private val headHolder by lazy { HeaderHolder(this) }
    private val sampleHolder by lazy { ImageSampleHolder(this) }
    private val actressHolder by lazy { ActressListHolder(this) }
    private val genreHolder by lazy { GenresHolder(this) }
    private val relativeMovieHolder by lazy { RelativeMovieHolder(this) }
    private val forumPostsHolder by lazy { ForumPostsHolder(this) }

    private val sr_refresh: SwipeRefreshLayout by lazy { findViewById<SwipeRefreshLayout>(R.id.sr_refresh) }
    private val app_bar: AppBarLayout by lazy { findViewById<AppBarLayout>(R.id.app_bar) }
    private val ll_movie_detail: LinearLayout by lazy { findViewById<LinearLayout>(R.id.ll_movie_detail) }
    private val iv_movie_cover: ImageView by lazy { findViewById<ImageView>(R.id.iv_movie_cover) }

    override val url by lazy { intent.getStringExtra(C.BundleKey.Key_1) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {

            mBasePresenter?.onRefresh()

        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = movie?.des
        immersionBar.transparentStatusBar().titleBar(toolbar).statusBarAlpha(0.12f).init()
        // ImmersionBar 的 titleBar 在这页没起作用, 收起封面后标题正好压在状态栏上。
        // 给 Toolbar 补一个状态栏高的上边距: CollapsingToolbarLayout 把 pinned 子 View 的
        // 「高度 + 上下 margin」算进折叠后的最小高度, 所以收起后顶栏撑高、标题不再被挡,
        // 展开态的封面仍然铺到屏幕顶 —— 那个出血是有意保留的
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            val barTop = resources.getDimensionPixelSize(resourceId)
            (toolbar.layoutParams as ViewGroup.MarginLayoutParams).let { lp ->
                if (lp.topMargin != barTop) {
                    lp.topMargin = barTop
                    toolbar.layoutParams = lp
                }
            }
        }
        initWidget()
        initData()

    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_movie_detail, menu)
        collectMenu = menu.findItem(R.id.action_add_movie_collect)
        removeCollectMenu = menu.findItem(R.id.action_remove_movie_collect)
        val saveItem = movie?.convertDBItem() ?: return true
        if (CollectModel.has(saveItem)) {
            collectMenu.isVisible = false
            removeCollectMenu.isVisible = true
        } else {
            collectMenu.isVisible = true
            removeCollectMenu.isVisible = false
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the CENSORED/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId
        val saveItem = movie?.convertDBItem() ?: return super.onOptionsItemSelected(item)


        when (id) {
            R.id.action_add_movie_collect -> {
                //收藏
                CollectModel.addToCollectForCategory(saveItem) {
                    collectMenu.isVisible = false
                    removeCollectMenu.isVisible = true
                }

            }
            R.id.action_remove_movie_collect -> {
                //取消收藏
                if (CollectModel.removeCollect(saveItem)) {
                    collectMenu.isVisible = true
                    removeCollectMenu.isVisible = false
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initWidget() {
        sr_refresh.setColorSchemeResources(
            R.color.colorPrimary,
            R.color.colorPrimaryDark,
            R.color.colorPrimaryLight
        )
        sr_refresh.setOnRefreshListener {
            //reload
            mBasePresenter?.onRefresh()

        }

        app_bar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, offset ->
            sr_refresh.isEnabled = offset >= 0
        })


        ll_movie_detail.addView(headHolder.view)
        ll_movie_detail.addView(sampleHolder.view)
        ll_movie_detail.addView(viewContext.inflate(R.layout.layout_load_magnet).apply {
            val lookMagnet = findViewById<TextView>(R.id.tv_movie_look_magnet)
            lookMagnet.setTextColor(
                ResourcesCompat.getColor(
                    resources,
                    R.color.colorPrimaryDark,
                    null
                )
            )
            lookMagnet.paintFlags = lookMagnet.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener {
                val code = movie?.code?.replace("-", " ") ?: url.orEmpty().urlPath
                MagnetPagerListActivity.start(this@MovieDetailActivity, code, movie?.link.orEmpty())
            }
        })
        ll_movie_detail.addView(actressHolder.view)
        ll_movie_detail.addView(genreHolder.view)
        ll_movie_detail.addView(relativeMovieHolder.view)
        ll_movie_detail.addView(forumPostsHolder.view)


    }

    private fun initData() {
        (intent.extras?.getSerializable(C.BundleKey.Key_1) as? Movie)?.let {
            movie = it
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        headHolder.release()
        sampleHolder.release()
        actressHolder.release()
        genreHolder.release()
        relativeMovieHolder.release()
        forumPostsHolder.release()
        ImmersionBar.with(this).destroy()
    }

    override fun createPresenter() = MovieDetailPresenterImpl(
        intent?.getBooleanExtra(C.BundleKey.Key_2, false)
            ?: false
    )

    override val layoutId = R.layout.activity_movie_detail

    override var movie: Movie? = null


    override fun showLoading() {
        if (!sr_refresh.isRefreshing) {
            sr_refresh.post {
                sr_refresh.setProgressViewOffset(false, 0, statusBarHeight)
                sr_refresh.isRefreshing = true
            }
        }
    }

    override fun dismissLoading() {
        sr_refresh.post { sr_refresh.isRefreshing = false }
    }

    override fun <T> showContent(data: T?) {
        if (data is Movie) {
            val convertDBItem = data.convertDBItem()
            if (movie?.imageUrl != data.imageUrl && CollectModel.has(convertDBItem)) {
                Schedulers.single().scheduleDirect {
                    //如果已收藏演员, 需要重新设置头像
                    CollectModel.update(convertDBItem)
                }
            }
            movie = data
            invalidateOptionsMenu()
        }

        if (data is MovieDetail) {
            //Slide Up Animation

            supportActionBar?.title = data.title
            //cover fixme
            iv_movie_cover.setOnClickListener {
                WatchLargeImageActivity.startShow(
                    this,
                    listOf(data.cover) + data.imageSamples.map { it.image })
            }
            GlideApp.with(this)
                .load(data.cover.toGlideNoHostUrl)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(DrawableImageViewTarget(iv_movie_cover))
            //animation
            ll_movie_detail.y = ll_movie_detail.y + 120
            ll_movie_detail.alpha = 0f
            ll_movie_detail.visibility = View.VISIBLE
            ll_movie_detail.animate().translationY(0f).alpha(1f).setDuration(500).start()

            headHolder.init(data.headers)
            sampleHolder.init(data.imageSamples)
            sampleHolder.cover = data.cover
            actressHolder.init(data.actress)
            genreHolder.init(data.genres)
            relativeMovieHolder.init(data.relatedMovies)
            forumPostsHolder.init(data.forumPosts)


        }
    }

    /*===========================other===================================*/
    companion object {
        fun start(current: Context, movie: Movie, fromHistory: Boolean = false) {
            current.startActivity(Intent(current, MovieDetailActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, movie)
                putExtra(C.BundleKey.Key_2, fromHistory)
            })
        }

        fun start(current: Context, movieUrl: String) {
            current.startActivity(Intent(current, MovieDetailActivity::class.java).apply {
                putExtra(C.BundleKey.Key_1, movieUrl)
                putExtra(C.BundleKey.Key_2, false)
            })
        }

    }

}
