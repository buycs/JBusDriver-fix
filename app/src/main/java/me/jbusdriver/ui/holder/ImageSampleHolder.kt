package me.jbusdriver.ui.holder

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.GlideApp
import me.jbusdriver.base.dpToPx
import me.jbusdriver.base.inflate
import me.jbusdriver.common.toGlideNoHostUrl
import me.jbusdriver.mvp.bean.ImageSample
import me.jbusdriver.ui.activity.WatchLargeImageActivity
import me.jbusdriver.ui.adapter.GridSpacingItemDecoration


/**
 * Created by Administrator on 2017/5/9 0009.
 */
class ImageSampleHolder(context: Context) : BaseHolder(context) {

    //cover
    lateinit var cover: String

    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_image_samples).apply {
                val rvRecycleImages = findViewById<RecyclerView>(R.id.rv_recycle_images)
                rvRecycleImages.layoutManager = GridLayoutManager(it, SPAN_COUNT)
                rvRecycleImages.addItemDecoration(
                    GridSpacingItemDecoration(SPAN_COUNT, it.dpToPx(8f), it.dpToPx(8f), false)
                )
                imageSampleAdapter.bindToRecyclerView(rvRecycleImages)
                rvRecycleImages.isNestedScrollingEnabled = true
                imageSampleAdapter.setOnItemClickListener { _, v, position ->
                    if (position < imageSampleAdapter.data.size) {
                        val destination = arrayListOf<String>()
                        var pos = position
                        if (this@ImageSampleHolder::cover.isInitialized) {
                            pos += 1
                            destination.add(cover)

                        }
                        imageSampleAdapter.data.mapTo(destination) { if (TextUtils.isEmpty(it.image)) it.thumb else it.image }
                        WatchLargeImageActivity.startShow(v.context, destination, pos)
                    }
                }
            }
        } ?: error("context ref is finish")
    }


    private val imageSampleAdapter =
        object : BaseQuickAdapter<ImageSample, BaseViewHolder>(R.layout.layout_image_sample_item) {
            override fun convert(holder: BaseViewHolder, item: ImageSample) {
                weakRef.get()?.apply {
                    holder.getView<ImageView>(R.id.iv_movie_thumb)?.let {
                        GlideApp.with(this).load(item.thumb.toGlideNoHostUrl)
                            .fitCenter()
                            .placeholder(R.drawable.ic_child_care_black_24dp)
                            .error(R.drawable.ic_child_care_black_24dp)
                            .into(DrawableImageViewTarget(it))

                    }
                }
            }
        }

    fun init(data: List<ImageSample>) {
        //imageSamples
        if (data.isEmpty()) view.findViewById<View>(R.id.tv_movie_images_none_tip).visibility = View.VISIBLE
        else {
            //load header
            imageSampleAdapter.setNewData(data)
        }
    }

    companion object {
        private const val SPAN_COUNT = 4
    }

}