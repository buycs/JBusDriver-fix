package me.jbusdriver.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.DateUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.CheckBox
import androidx.recyclerview.widget.GridLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.chad.library.adapter.base.entity.MultiItemEntity
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseActivity
import me.jbusdriver.base.common.C
import me.jbusdriver.common.JBus
import me.jbusdriver.databinding.ActivitySettingBinding
import me.jbusdriver.databinding.LayoutCollectBackEditItemBinding
import me.jbusdriver.db.service.LinkService
import me.jbusdriver.mvp.bean.BackUpEvent
import me.jbusdriver.mvp.bean.Expand_Type_Head
import me.jbusdriver.mvp.bean.MenuOp
import me.jbusdriver.mvp.bean.MenuOpHead
import me.jbusdriver.ui.adapter.MenuOpAdapter
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.task.LoadCollectService
import java.io.File
import java.util.concurrent.TimeUnit

class SettingActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingBinding

    private var pageModeHolder = AppConfiguration.pageMode
    private val menuOpValue by lazy { AppConfiguration.menuConfig.toMutableMap() }


    private val backDir by lazy {
        val pathSuffix = File.separator + "collect" + File.separator + "backup" + File.separator
        val dir: String =
            createDir(Environment.getExternalStorageDirectory().absolutePath + File.separator + JBus.packageName + pathSuffix)
                ?: createDir(JBus.filesDir.absolutePath + pathSuffix)
                ?: error("cant not create collect dir in anywhere")
        File(dir)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setToolBar()
        initSettingView()
        RxBus.toFlowable(BackUpEvent::class.java).throttleLast(100, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                binding.tvCollectBackup.text = "正在加载备份${it.path}的第${it.index}/${it.total}个"
                if (it.total == it.index) {
                    binding.tvCollectBackup.text = "点击备份"
                    binding.tvCollectBackup.isClickable = it.total == it.index
                }

            }, {

                binding.tvCollectBackup.text = "点击备份"
                binding.tvCollectBackup.isClickable = true
            }).addTo(rxManager)
    }

    @SuppressLint("ResourceAsColor")
    private fun initSettingView() {

        //page mode
        changePageMode(AppConfiguration.pageMode)
        binding.llPageModePage.setOnClickListener {
            pageModeHolder = AppConfiguration.PageMode.Page
            changePageMode(AppConfiguration.PageMode.Page)
        }
        binding.llPageModeNormal.setOnClickListener {
            pageModeHolder = AppConfiguration.PageMode.Normal
            changePageMode(AppConfiguration.PageMode.Normal)
        }

        //menu op
        val data: List<MultiItemEntity> = arrayListOf(
            MenuOpHead("个人").apply { MenuOp.mine.forEach { addSubItem(it) } },
            MenuOpHead("有碼").apply { MenuOp.nav_ma.forEach { addSubItem(it) } },
            MenuOpHead("無碼").apply { MenuOp.nav_uncensore.forEach { addSubItem(it) } },
            MenuOpHead("其他").apply { MenuOp.nav_other.forEach { addSubItem(it) } }
        )
        val adapter = MenuOpAdapter(data)
        adapter.bindToRecyclerView(binding.rvMenuOp)
        binding.rvMenuOp.layoutManager = GridLayoutManager(viewContext, viewContext.spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.getItemViewType(position) == Expand_Type_Head) spanCount else 1
            }
        }
        //有选项选中就展开
        val expandItems = data.filterIndexed { _, multiItemEntity ->
            multiItemEntity is MenuOpHead && multiItemEntity.subItems.any { it.isHow }
        }
        expandItems.forEach {
            adapter.expand(data.indexOf(it))
        }
        adapter.setOnItemClickListener { _, view, position ->
            (adapter.data.getOrNull(position) as? MenuOp)?.let {
                view.findViewById<CheckBox>(R.id.cb_nav_menu)?.let { cb ->
                    //添加设置
                    synchronized(cb) {
                        cb.isChecked = !cb.isChecked
                        menuOpValue[it.name] = cb.isChecked
                    }
                }
            }

        }

        //收藏分类
        binding.swCollectCategory.isChecked = AppConfiguration.enableCategory
        binding.swCollectCategory.setOnCheckedChangeListener { _, isChecked ->
            AppConfiguration.enableCategory = isChecked
        }

        //备份
        binding.tvCollectBackup.setOnClickListener {

            val loading = MaterialDialog.Builder(viewContext).content("正在备份...").progress(true, 0).show()
            Flowable.fromCallable { backDir }
                .flatMap { file ->
                    return@flatMap LinkService.queryAll().doOnNext {
                        File(file, "backup${System.currentTimeMillis()}.json").writeText(it.toJsonString())
                    }
                }.compose(SchedulersCompat.single())
                .doAfterTerminate { loading.dismiss() }
                .subscribeBy(onError = { toast("备份失败,请重新打开app") }, onNext = {
                    toast("备份成功")
                    loadBackUp()
                })
                .addTo(rxManager)
        }

        loadBackUp()

    }

    private fun secondSpannableString(str: String): SpannableString {
        return SpannableString(str).apply {
            setSpan(
                RelativeSizeSpan(0.8f),
                0,
                str.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(R.color.secondText.toColorInt()),
                0,
                str.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun showBackupFileInfo(file: File) {
        val tip = "拷贝备份目录或备份文件至其他手机的相同sd目录即可。如sdcard/me.jbusdriver/collect/backup/xxx.json"

        val str = SpannableStringBuilder("1. 路径")
            .append(System.getProperty("line.separator"))
            .append(secondSpannableString(file.absolutePath))
            .append(System.getProperty("line.separator"))
            .append(System.getProperty("line.separator"))
            .append("2. 迁移至其他手机")
            .append(System.getProperty("line.separator"))
            .append(secondSpannableString(tip))



        MaterialDialog.Builder(viewContext)
            .title("信息")
            .content(str)
            .show()
    }


    private fun loadBackUp() {


        binding.llCollectBackupFiles.removeAllViews()
        Flowable.fromCallable { backDir }
            .map {
                val list = it.walk().maxDepth(1).filter {
                    it.isFile && it.name.contains("backup.+json".toRegex())
                }.toList()
                if (list.isEmpty()) {
                    listOf(LayoutCollectBackEditItemBinding.inflate(layoutInflater).apply {
                        tvBackupName.text = "没有备份呢~~"
                        ivBackupLoad.visibility = View.GONE
                        ivBackupDelete.visibility = View.GONE
                        ivBackupInfo.setOnClickListener {
                            MaterialDialog.Builder(viewContext)
                                .title("信息")
                                .content("还没有备份哦？来一发试试！")
                                .show()
                        }
                    }.root)
                } else {
                    list.mapIndexed { index, file ->
                        LayoutCollectBackEditItemBinding.inflate(layoutInflater).apply {

                            val date = DateUtils.formatDateTime(
                                viewContext, file.lastModified(),
                                DateUtils.FORMAT_SHOW_YEAR or
                                        DateUtils.FORMAT_SHOW_DATE or
                                        DateUtils.FORMAT_SHOW_TIME
                            )



                            tvBackupName.setOnClickListener {
                                showBackupFileInfo(file)
                            }
                            ivBackupInfo.setOnClickListener {
                                showBackupFileInfo(file)
                            }

                            tvBackupName.text = SpannableStringBuilder("${index + 1}. ${file.name}")
                                .append(System.getProperty("line.separator"))
                                .append("    ")
                                .append(secondSpannableString(date))


                            ivBackupLoad.setOnClickListener {
                                MaterialDialog.Builder(viewContext)
                                    .title("加载备份")
                                    .content("${file.name}\n注意:相同文件会被覆盖")
                                    .positiveText("确定")
                                    .negativeText("取消")
                                    .negativeColor(R.color.secondText.toColorInt())
                                    .onPositive { _, _ ->
                                        LoadCollectService.startLoadBackUp(viewContext, file)
                                    }
                                    .show()

                            }
                            ivBackupDelete.setOnClickListener {
                                MaterialDialog.Builder(viewContext)
                                    .title("注意")
                                    .content("确定要删除${file.name}吗?")
                                    .positiveText("确定")
                                    .negativeText("取消")
                                    .negativeColor(R.color.secondText.toColorInt())
                                    .onPositive { _, _ ->
                                        file.deleteRecursively()
                                        loadBackUp()
                                    }
                                    .show()
                            }
                        }.root
                    }
                }

            }.compose(SchedulersCompat.single())
            .subscribeBy {
                it.forEach {
                    binding.llCollectBackupFiles.addView(it)
                }
            }
            .addTo(rxManager)
    }

    private fun changePageMode(mode: Int) {
        when (mode) {
            AppConfiguration.PageMode.Page -> {
                binding.llPageModePage.setBackgroundResource(R.drawable.mode_page_shape_corner)
                binding.llPageModeNormal.setBackgroundResource(0)
            }
            AppConfiguration.PageMode.Normal -> {
                binding.llPageModePage.setBackgroundResource(0)
                binding.llPageModeNormal.setBackgroundResource(R.drawable.mode_page_shape_corner)
            }
        }
    }

    private fun setToolBar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "设置"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onStop() {
        super.onStop()
        AppConfiguration.pageMode = pageModeHolder
        if (AppConfiguration.menuConfig != menuOpValue) AppConfiguration.saveSaveMenuConfig(menuOpValue) //必须调用equals
    }

    companion object {
        fun start(context: Context) = context.startActivity(Intent(context, SettingActivity::class.java))
    }
}
