package me.jbusdriver.ui.holder

import android.content.Context
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.copy
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.common.JBus
import me.jbusdriver.mvp.bean.Header
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.bean.des
import me.jbusdriver.mvp.model.CollectModel
import me.jbusdriver.ui.activity.MovieListActivity
import me.jbusdriver.ui.data.contextMenu.LinkMenu

/**
 * Created by Administrator on 2017/5/9 0009.
 */
class HeaderHolder(context: Context) : BaseHolder(context) {


    val view by lazy {
        weakRef.get()?.let {
            it.inflate(R.layout.layout_detail_header).apply {
                val rvRecycleHeader = findViewById<RecyclerView>(R.id.rv_recycle_header)
                rvRecycleHeader.layoutManager = LinearLayoutManager(this.context)
                headAdapter.bindToRecyclerView(rvRecycleHeader)
                rvRecycleHeader.isNestedScrollingEnabled = true
            }
        } ?: error("context ref is finish")
    }

    private val headAdapter = object : BaseQuickAdapter<Header, BaseViewHolder>(R.layout.layout_header_item) {
        override fun convert(holder: BaseViewHolder, item: Header) {
            holder.getView<TextView>(R.id.tv_head_value)?.apply {
                if (!TextUtils.isEmpty(item.link)) {
                    setTextColor(ResourcesCompat.getColor(this@apply.resources, R.color.colorPrimaryDark, null))
                    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG

                    setOnClickListener {
                        MovieListActivity.start(it.context, item)
                    }

                } else {
                    setTextColor(ResourcesCompat.getColor(this@apply.resources, R.color.secondText, null))
                    paintFlags = 0
                    setOnClickListener(null)
                }
                //长按操作
                setOnLongClickListener {
                    // 识别码/时长/发行日期/名称在站点上就是一行短文本, 长按直接复制比弹菜单快;
                    // 其余字段(导演/制作商等)带跳转地址、还要能收藏, 保持原来的弹菜单
                    if (item.name in DIRECT_COPY_NAMES) {
                        JBus.copy(item.value)
                        toast("已复制")
                        return@setOnLongClickListener true
                    }

                    val action = LinkMenu.linkActions.filter {
                        when {
                            TextUtils.isEmpty(item.link) -> it.key == "复制"
                            CollectModel.has(item.convertDBItem()) -> it.key != "收藏"
                            else -> it.key != "取消收藏"
                        }
                    }.toMutableMap()

                    val ac = action.remove("收藏")
                    if (ac != null) {
                        action["收藏到分类..."] = ac
                    }

                    MaterialDialog.Builder(holder.itemView.context).title(item.name).content(item.des)
                        .items(action.keys)
                        .itemsCallback { _, _, _, text ->
                            action[text]?.invoke(item)
                        }.show()
                    return@setOnLongClickListener true

                }
            }
            holder.setText(R.id.tv_head_name, item.name)
                .setText(R.id.tv_head_value, item.value)
        }
    }

    fun init(data: List<Header>) {
        //header
        if (data.isEmpty()) view.findViewById<View>(R.id.tv_movie_head_none_tip).visibility = View.VISIBLE
        else {
            //load header
            headAdapter.setNewData(data)
        }
    }

    companion object {
        /** 站点原文的字段名(繁体), 这几个长按直接复制 */
        private val DIRECT_COPY_NAMES = setOf("識別碼", "長度", "發行日期", "名稱")
    }

}