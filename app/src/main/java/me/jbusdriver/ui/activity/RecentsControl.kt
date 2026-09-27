package me.jbusdriver.ui.activity

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

private const val TAG = "RecentsControl"

/**
 * 让本应用的任务在系统「最近任务」列表中隐藏（或恢复显示）。
 *
 * ## 为什么不能只改 intent flag
 * 最近任务条目对应的是**整个 task**，判定依据是创建该 task 时的基准 intent。
 * 本应用的启动入口是 [SplashActivity]（`android.intent.category.LAUNCHER`），
 * 它拉起 [MainActivity] 后立刻 `finish()`；task 的基准 intent 始终是启动
 * [SplashActivity] 的那个 LAUNCHER intent。
 *
 * 因此只在 [MainActivity] 上 `addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)` 是无效的 ——
 * 它改不到 task 的基准 intent，表现就是「设置里开关已开，最近任务里却还看得见」。
 *
 * ## 实现策略
 * - **Android 11（API 30）及以上**：用官方的
 *   [ActivityManager.AppTask.setExcludeFromRecents]，运行时立即生效。
 * - **Android 10（API 29）及以下**：系统没有运行时 API，退化成改写
 *   [Activity.getIntent] 的标志位。该 flag 只在 task 创建时被读取，
 *   对已存在的 task 通常不生效，需重启应用后才可能生效
 *   （设置页文案里的「重启后生效」即由此而来）。
 *
 * @param excluded true 表示从最近任务列表隐藏，false 表示恢复显示
 */
internal fun Activity.applyRecentsExclusion(excluded: Boolean) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        applyViaAppTask(excluded)
    } else {
        applyViaIntentFlag(excluded)
    }
}

/**
 * API 30+ 的官方途径：直接设置本 Activity 所属 task 的 excludeFromRecents。
 *
 * 标 [RequiresApi] 是为了让 Lint 知道这里只会在 API 30+ 被调用（调用点已用 `SDK_INT >= R` 把关），
 * 否则 `TaskInfo.taskId`（API 29 起）会被报成 NewApi 误报。
 */
@RequiresApi(Build.VERSION_CODES.R)
private fun Activity.applyViaAppTask(excluded: Boolean) {
    try {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        manager.appTasks.firstOrNull { it.taskInfo.taskId == taskId }
            ?.setExcludeFromRecents(excluded)
    } catch (e: Exception) {
        // 个别 ROM 会限制 AppTask 操作，失败不应导致崩溃
        Log.w(TAG, "setExcludeFromRecents failed: ${e.message}")
    }
}

/** API 29- 的兜底途径：改写 intent 标志位，重启后生效。 */
private fun Activity.applyViaIntentFlag(excluded: Boolean) {
    try {
        intent?.let { current ->
            if (excluded) {
                current.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            } else {
                current.flags = current.flags and Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS.inv()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "intent flag fallback failed: ${e.message}")
    }
}
