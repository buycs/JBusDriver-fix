package me.jbusdriver.ui.fragment

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.bumptech.glide.Glide
import io.reactivex.Flowable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import me.jbusdriver.R
import me.jbusdriver.base.*
import me.jbusdriver.base.common.BaseFragment
import me.jbusdriver.databinding.FragmentSettingBinding
import me.jbusdriver.databinding.LayoutSettingItemBinding
import me.jbusdriver.db.bean.LinkItem
import me.jbusdriver.db.service.LinkService
import me.jbusdriver.http.GitHub
import me.jbusdriver.mvp.bean.UpdateBean
import me.jbusdriver.ui.activity.SplashActivity
import me.jbusdriver.ui.data.AppConfiguration
import me.jbusdriver.ui.data.BottomTab
import me.jbusdriver.ui.data.BottomTabs
import me.jbusdriver.ui.task.LoadCollectService
import java.io.File

/**
 * 设置页的内容。抽屉样式下由 SettingActivity 承载, 底部样式下作为「设置」页签, 所以逻辑都放这。
 * 每一行只显示标题和当前值, 选项统一走弹窗单选, 和参考 app 的交互一致。
 */
class SettingFragment : BaseFragment() {

    private var binding: FragmentSettingBinding? = null

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_JSON)) { uri ->
            uri?.let { writeBackupTo(it) }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readBackupFrom(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSettingBinding.inflate(inflater, container, false)
        .also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindRows()
        refresh()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun bindRows() {
        val b = binding ?: return
        b.rowUiMode.root.setOnClickListener { pickUiMode() }
        b.rowListStyle.root.setOnClickListener { pickPageMode() }
        b.rowHomePage.root.setOnClickListener { pickHomePage() }
        b.rowCollectBackup.root.setOnClickListener { pickBackupAction() }
        b.rowTheme.root.setOnClickListener { pickTheme() }
        b.rowGridColumn.root.setOnClickListener { pickGridColumn() }
        b.rowClearCache.root.setOnClickListener { clearCache() }
        b.rowCheckUpdate.root.setOnClickListener { checkUpdate() }
        b.rowAbout.root.setOnClickListener { showAbout() }

        bindSwitch(
            b.rowHideRecent, "最近任务隐藏", HIDE_RECENT_SUMMARY,
            AppConfiguration.hideRecent
        ) {
            AppConfiguration.hideRecent = it
            toast("重启应用后生效")
        }
    }

    private fun bindSwitch(
        item: me.jbusdriver.databinding.LayoutSettingSwitchItemBinding,
        title: String,
        summary: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        item.tvSettingSwitchTitle.text = title
        item.tvSettingSwitchSummary.text = summary
        item.swSettingSwitch.isChecked = checked
        item.swSettingSwitch.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        // 整行也要能点: 只让 Switch 那小块可点的话, 用户点标题没反应
        item.root.setOnClickListener { item.swSettingSwitch.toggle() }
    }

    /** 当前值全部从配置里读, 每次改完再刷一遍, 免得行文案和实际值对不上 */
    private fun refresh() {
        val b = binding ?: return
        val bottomMode = AppConfiguration.uiMode == AppConfiguration.UiMode.Bottom

        title(b.rowUiMode, "UI交互样式", "当前：${UI_MODE_LABELS[AppConfiguration.uiMode]}")
        title(
            b.rowHomePage, "首页设置",
            if (bottomMode) "当前：${homePageTab().label}" else HOME_PAGE_DISABLED_SUMMARY,
            enabled = bottomMode
        )
        title(
            b.rowListStyle, "影片列表样式",
            "当前：${PAGE_MODE_LABELS[AppConfiguration.pageMode]}"
        )
        title(b.rowCollectBackup, "导入/导出收藏", "导出为 JSON 文件, 或从文件恢复")
        title(b.rowTheme, "主题", "当前：${THEME_LABELS[AppConfiguration.themeMode]}")
        title(b.rowGridColumn, "网格列数", "当前：${AppConfiguration.gridColumn} 列")
        title(b.rowClearCache, "清理缓存", CLEAR_CACHE_SUMMARY)
        title(b.rowCheckUpdate, "检查更新", CHECK_UPDATE_SUMMARY)
        title(b.rowAbout, "关于", "版本 ${versionName()} (${versionCode()})")
    }

    private fun title(
        item: LayoutSettingItemBinding,
        text: String,
        summary: String,
        enabled: Boolean = true
    ) {
        item.tvSettingTitle.text = text
        item.tvSettingSummary.text = summary
        item.root.isEnabled = enabled
        item.root.alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    //region 弹窗选择
    private fun singleChoice(
        title: String,
        labels: List<String>,
        current: Int,
        onPick: (Int) -> Unit
    ) {
        MaterialDialog.Builder(viewContext)
            .title(title)
            .items(labels)
            .itemsCallbackSingleChoice(current) { _, _, which, _ ->
                onPick(which)
                true
            }
            .positiveText("保存")
            .negativeText("取消")
            .show()
    }

    private fun pickUiMode() {
        singleChoice("UI交互样式", UI_MODE_LABELS.toList(), AppConfiguration.uiMode) { which ->
            if (which == AppConfiguration.uiMode) return@singleChoice
            AppConfiguration.uiMode = which
            refresh()
            confirmRestart()
        }
    }

    private fun pickPageMode() {
        singleChoice("影片列表样式", PAGE_MODE_LABELS.toList(), AppConfiguration.pageMode) { which ->
            AppConfiguration.pageMode = which
            refresh()
        }
    }

    private fun pickTheme() {
        singleChoice("主题", THEME_LABELS.toList(), AppConfiguration.themeMode) { which ->
            // setDefaultNightMode 会把在跑的 Activity 重建, 这里只记值
            AppConfiguration.themeMode = which
            refresh()
        }
    }

    private fun pickGridColumn() {
        val columns = AppConfiguration.gridColumn
        singleChoice("网格列数", GRID_COLUMN_LABELS, columns - AppConfiguration.GridColumn.MIN) { which ->
            AppConfiguration.gridColumn = which + AppConfiguration.GridColumn.MIN
            refresh()
        }
    }

    private fun pickHomePage() {
        val tabs = BottomTabs.SELECTABLE
        singleChoice("首页设置", tabs.map { it.label }, tabs.indexOf(homePageTab())) { which ->
            AppConfiguration.homePageId = tabs[which].id
            toast("保存成功")
            refresh()
        }
    }

    /** 旧配置可能存着已下线可选项的 id, 落不回可选列表就退回影片 */
    private fun homePageTab(): BottomTab =
        BottomTabs.SELECTABLE.firstOrNull { it.id == AppConfiguration.homePageId } ?: BottomTabs.MOVIE

    private fun confirmRestart() {
        MaterialDialog.Builder(viewContext)
            .title("需要重启")
            .content("界面样式在应用重启后生效, 现在重启吗?")
            .positiveText("立即重启")
            .negativeText("稍后")
            .onPositive { _, _ ->
                SplashActivity.start(viewContext)
                activity?.finish()
            }
            .show()
    }
    //endregion

    //region 收藏导入导出
    private fun pickBackupAction() {
        MaterialDialog.Builder(viewContext)
            .title("导入/导出收藏")
            .items("导出收藏", "导入收藏")
            .itemsCallback { _, _, which, _ ->
                when (which) {
                    0 -> exportLauncher.launch(BACKUP_FILE_NAME)
                    else -> importLauncher.launch(BACKUP_MIMES)
                }
            }
            .negativeText("取消")
            .show()
    }

    private fun writeBackupTo(uri: Uri) {
        val loading = MaterialDialog.Builder(viewContext).content("正在导出...").progress(true, 0).show()
        Flowable.fromCallable {
            val json = LinkService.queryAll().toJsonString()
            viewContext.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                ?: error("无法写入所选文件")
            json
        }.compose(SchedulersCompat.io())
            .doAfterTerminate { loading.dismiss() }
            .subscribeBy(
                onError = { toast("导出失败:${it.message}") },
                onNext = { toast("已导出收藏, 共 ${it.length} 字节") }
            ).addTo(rxManager)
    }

    /**
     * 恢复仍然走 LoadCollectService: 它按 key 做「有则更新无则插入」, 直接换个入口不该改这套语义。
     * SAF 给的内容流是一次性授权, 所以先落到自己的缓存文件再把 File 交给服务。
     */
    private fun readBackupFrom(uri: Uri) {
        Flowable.fromCallable {
            val text = viewContext.contentResolver.openInputStream(uri)
                ?.use { it.readBytes() }?.toString(Charsets.UTF_8)
                ?: error("无法读取所选文件")
            val list = GSON.fromJson<List<LinkItem>>(text)
            if (list.isNullOrEmpty()) error("没有要恢复的内容")
            File(requireContext().cacheDir, "import-${System.currentTimeMillis()}.json")
                .apply { writeText(text) }
        }.compose(SchedulersCompat.io())
            .subscribeBy(
                onError = { toast("导入失败:${it.message}") },
                onNext = { LoadCollectService.startLoadBackUp(viewContext, it) }
            ).addTo(rxManager)
    }
    //endregion

    private fun clearCache() {
        val loading = MaterialDialog.Builder(viewContext).content("正在清理...").progress(true, 0).show()
        Flowable.fromCallable {
            CacheLoader.lru.evictAll()
            CacheLoader.acache.clear()
            // Glide 明确要求这个方法不能在主线调用
            Glide.get(viewContext.applicationContext).clearDiskCache()
        }.compose(SchedulersCompat.io())
            .doAfterTerminate { loading.dismiss() }
            .subscribeBy(
                onError = { toast("清理失败") },
                onNext = { toast("缓存已清除") }
            ).addTo(rxManager)
    }

    @SuppressLint("SetTextI18n")
    private fun checkUpdate() {
        val b = binding ?: return
        b.rowCheckUpdate.tvSettingSummary.text = "正在检查更新…"
        GitHub.INSTANCE.properties().addUserCase()
            .map { GSON.fromJson<UpdateBean>(it) }
            .compose(SchedulersCompat.io<UpdateBean>())
            .subscribeBy(
                onError = {
                    toast("检查更新失败")
                    refresh()
                },
                onNext = { showUpdateResult(it) }
            ).addTo(rxManager)
    }

    private fun showUpdateResult(bean: UpdateBean?) {
        refresh()
        if (bean == null || bean.versionCode <= 0) {
            toast("检查更新失败")
            return
        }
        if (versionCode() < bean.versionCode) {
            MaterialDialog.Builder(viewContext)
                .title("发现新版本(${bean.versionName ?: bean.versionCode})")
                .content(bean.changelog?.takeIf { it.isNotBlank() } ?: "点击「打开」前往下载页")
                .positiveText("打开")
                .negativeText("取消")
                .onPositive { _, _ -> viewContext.browse("$FORK_GIT_URL/releases") }
                .show()
        } else {
            toast("已是最新版本 ${versionName()}")
        }
    }

    private fun showAbout() {
        val content = SpannableStringBuilder()
            .append("版本：${versionName()} (${versionCode()})\n\n")
            .append("本应用为 JavBus 客户端的二次开发, 数据均来自第三方站点, 仅供学习交流, 请勿用于非法用途。\n\n")
            .append("项目源码\n")
            .appendLink("本项目源码", FORK_GIT_URL)
            .append("\n")
            .appendLink("原项目源码", GIT_URL)

        val dialog = MaterialDialog.Builder(viewContext)
            .title("关于")
            .content(content)
            .negativeText("关闭")
            .show()
        // 链接要靠 MovementMethod 才响应点击, MaterialDialog 0.9 的正文默认没有
        dialog.getContentView()?.movementMethod = LinkMovementMethod.getInstance()
    }

    /** 正文里的可点链接: 点击走应用内的 browse, 不用 URLSpan（它自己起 intent, 失败没有提示） */
    private fun SpannableStringBuilder.appendLink(label: String, url: String): SpannableStringBuilder {
        val start = length
        append(label)
        setSpan(object : ClickableSpan() {
            override fun onClick(widget: View) {
                viewContext.browse(url)
            }

            override fun updateDrawState(ds: TextPaint) {
                ds.color = ContextCompat.getColor(viewContext, R.color.colorPrimary)
                ds.isUnderlineText = false
            }
        }, start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return this
    }

    private fun versionName(): String = packageInfo()?.versionName ?: "未知版本"

    private fun versionCode(): Int = packageInfo()?.versionCode ?: -1

    @Suppress("DEPRECATION")
    private fun packageInfo() = runCatching {
        viewContext.packageManager.getPackageInfo(viewContext.packageName, 0)
    }.getOrNull()

    companion object {
        fun newInstance() = SettingFragment()

        private val UI_MODE_LABELS = arrayOf("侧边样式", "底部样式")
        //下标必须等于 AppConfiguration.PageMode 的存值: Normal=0, Page=1
        private val PAGE_MODE_LABELS = arrayOf("普通模式", "分页模式")
        private val THEME_LABELS = arrayOf("跟随系统", "浅色", "深色")
        //下标 + GridColumn.MIN = 实际列数
        private val GRID_COLUMN_LABELS = listOf("1 列", "2 列", "3 列", "4 列")

        private const val HOME_PAGE_DISABLED_SUMMARY = "需先切换到底部样式"
        private const val CLEAR_CACHE_SUMMARY = "清除图片与网络缓存, 释放本地空间"
        private const val CHECK_UPDATE_SUMMARY = "从服务器检查是否有新版本"
        private const val HIDE_RECENT_SUMMARY = "最近任务列表不显示本应用（切换后重启应用生效）"

        private const val MIME_JSON = "application/json"
        private const val BACKUP_FILE_NAME = "jbusdriver-collect-backup.json"
        private val BACKUP_MIMES = arrayOf(MIME_JSON, "text/plain", "*/*")

        private const val GIT_URL = "https://github.com/Ccixyj/JBusDriver"
        private const val FORK_GIT_URL = "https://github.com/buycs/JBusDriver-fix"
        private const val DISABLED_ALPHA = 0.4f
    }
}
