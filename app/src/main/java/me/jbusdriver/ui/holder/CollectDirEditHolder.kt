package me.jbusdriver.ui.holder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import me.jbusdriver.R
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.common.bean.db.AllFirstParentDBCategoryGroup
import me.jbusdriver.common.bean.db.Category


/**
 * Created by Administrator on 2017/11/2 0002.
 */


class CollectDirEditHolder(context: Context, parentCategory: Category) : BaseHolder(context) {

    private val delActionsParams = mutableSetOf<Category>()
    private val addActionsParams = mutableSetOf<Category>()
    private val collectDirs by lazy { categoryAdapter.data }


    val view by lazy {
        weakRef.get()?.let { context ->
            context.inflate(R.layout.layout_collect_dir_edit).apply {
                val llAddCategory = findViewById<LinearLayout>(R.id.ll_add_category)
                val llAddCategoryEdit = findViewById<LinearLayout>(R.id.ll_add_category_edit)
                val tvAddCategoryName = findViewById<EditText>(R.id.tv_add_category_name)
                findViewById<TextView>(R.id.tv_category_add).setOnClickListener {
                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(llAddCategory, "alpha", 1.0f, 0.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "translationY", 60f, 0f).apply {
                                addListener(

                                    object : AnimatorListenerAdapter() {
                                        override fun onAnimationStart(animation: Animator) {
                                            llAddCategoryEdit.visibility = View.VISIBLE
                                        }

                                        override fun onAnimationEnd(animation: Animator) {
                                            llAddCategory.visibility = View.GONE
                                        }
                                    }
                                )

                            })
                        duration = 300

                    }.start()

                }

                findViewById<TextView>(R.id.tv_category_add_confirm).setOnClickListener {
                    val txt = tvAddCategoryName.text.toString().trim()
                    val add = if (txt.isNotBlank()) {
                        if (collectDirs.any { it.name == txt }) {
                            toast("$txt 分类已存在")
                            false
                        } else true

                    } else {
                        toast("请输入收藏夹名称")
                        false
                    }

                    if (add) {
                        val category = Category(
                            txt, parentCategory.id
                                ?: -1, "${parentCategory.id}/"
                        )
                        addActionsParams.add(category)
                        categoryAdapter.addData(category)
                        categoryAdapter.notifyItemChanged(categoryAdapter.data.size - 1)
                        tvAddCategoryName.setText("")
                    }

                    AnimatorSet().apply {
                        playTogether(
                            ObjectAnimator.ofFloat(llAddCategory, "alpha", 0.0f, 1.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "alpha", 1.0f, 0.0f),
                            ObjectAnimator.ofFloat(llAddCategoryEdit, "translationY", 0f, -60f).apply {
                                addListener(
                                    object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            llAddCategory.visibility = View.VISIBLE
                                            llAddCategoryEdit.visibility = View.GONE
                                        }
                                    }
                                )

                            }
                        )
                        duration = 300
                    }.start()
                }

                findViewById<RecyclerView>(R.id.rv_category_list).apply {
                    layoutManager = LinearLayoutManager(context)
                    categoryAdapter.bindToRecyclerView(this)
                    categoryAdapter.setOnItemChildClickListener { _, view, position ->
                        when (view.id) {
                            R.id.tv_category_delete -> {
                                //删除
                                categoryAdapter.data.getOrNull(position)?.let {
                                    //具体删除逻辑
                                    if (it.id in (1..10)) return@setOnItemChildClickListener
                                    categoryAdapter.data.removeAt(position)
                                    categoryAdapter.notifyItemRemoved(position)
                                    delActionsParams.add(it)
                                }

                            }
                            else -> Unit
                        }
                    }

                }
            }
        } ?: error("CollectDirEditHolder can not inflate view for context is null ")
    }

    private val categoryAdapter by lazy {
        object : BaseQuickAdapter<Category, BaseViewHolder>(R.layout.layout_collect_dir_edit_item) {
            private val exclude = AllFirstParentDBCategoryGroup.mapNotNull { it.value.id }
            override fun convert(holder: BaseViewHolder, item: Category) {
                holder.setText(R.id.tv_category_name, item.name)
                    .setVisible(R.id.tv_category_delete, item.id !in exclude)
                    .addOnClickListener(R.id.tv_category_delete)
            }
        }
    }

    /**
     * @param callback : 确认回调; first : 删除的元素 ,second : 添加的元素
     *
     */
    fun showDialogWithData(data: Collection<Category>, callback: (Set<Category>, Set<Category>) -> Unit) {
        //清空Action的参数
        delActionsParams.clear()
        addActionsParams.clear()
        //清空原有的数据
        categoryAdapter.data.clear()
        categoryAdapter.addData(data)

        MaterialDialog.Builder(view.context).customView(view, true)
            .dismissListener {
                callback.invoke(delActionsParams, addActionsParams)
            }
            .show()

    }
}