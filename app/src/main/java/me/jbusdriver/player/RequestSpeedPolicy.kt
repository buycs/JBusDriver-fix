package me.jbusdriver.player

import java.util.Locale

/**
 * 「实时请求网速」的换算与格式化。
 *
 * 单独成文件是为了**能单测**：采样真正的来源（Media3 的 `DataSource`、libtorrent 的
 * `TorrentStatus`）都只能在设备上跑起来，而「窗口怎么取、字节怎么折算」恰恰是最容易
 * 算错的地方（除零、窗口外还参与相减、采样倒退算出负速率）。
 */

/** 一次采样：时刻（`elapsedRealtime` 毫秒）+ 到这一刻为止的**累计**字节数。 */
internal data class SpeedSample(val timeMs: Long, val totalBytes: Long)

/**
 * 最近 [windowMs] 内的平均速率，单位字节/秒；算不出来就返回 0（当作「此刻没货」）。
 *
 * 为什么要开窗口而不是直接用「相邻两次采样的差」：HLS 是分片拉取的，字节数会**跳着**涨
 * （一整个分片下完才前进），单点差分看着像抖动；反过来拿全部历史做平均又会把突发抹平，
 * 真卡住了还显示着刚才的高速。
 *
 * 基准点取「窗口内最早的那次采样」：只丢采样点、不丢字节，所以窗口滑动时不会漏账。
 */
internal fun requestSpeedBytesPerSecond(samples: List<SpeedSample>, windowMs: Long): Long {
    if (samples.size < 2) return 0L
    val newest = samples.last()
    var baseIndex = samples.size - 2
    while (baseIndex > 0 && newest.timeMs - samples[baseIndex - 1].timeMs <= windowMs) baseIndex--
    val base = samples[baseIndex]
    val elapsedMs = newest.timeMs - base.timeMs
    if (elapsedMs <= 0L) return 0L
    // 累计字节是单调的（seek、重新 open 都不会让它倒退）；真倒退了当作没数据。
    val bytes = newest.totalBytes - base.totalBytes
    if (bytes <= 0L) return 0L
    return bytes * 1000L / elapsedMs
}

/**
 * 网速文案。
 *
 * 三档而不是「最小 1 B/s」：缓冲时用户要看的是「到底有没有货」，
 * 500 B/s 写成「500 B/s」比「0.5 KB/s」更难一眼读出来，但写成 `0 KB/s` 又会和
 * 「完全停滞」混在一起 —— 所以低档保留一位小数，0 就是 0.0 KB/s。
 */
internal fun formatRequestSpeed(bytesPerSecond: Long): String {
    val kb = bytesPerSecond.toDouble() / 1024.0
    return when {
        bytesPerSecond >= 1_048_576L -> String.format(Locale.US, "%.1f MB/s", kb / 1024.0)
        bytesPerSecond >= 10_240L -> String.format(Locale.US, "%.0f KB/s", kb)
        else -> String.format(Locale.US, "%.1f KB/s", kb)
    }
}
