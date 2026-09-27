package me.jbusdriver.torrent

import kotlin.math.max
import kotlin.math.min

/**
 * 磁力流的两条纯判定。
 *
 * 单独放一个文件是为了**能单测**：[MagnetStream] / [MagnetPlayback] 所在的类都挂着
 * libtorrent4j 的 native 依赖（还夹着 Media3 的 `Context`），JVM 单测加载不了；
 * 而这两条逻辑（偏移换算、失败原因该不该清）恰恰是最容易写错、最值得钉住的部分。
 */

/**
 * 从主文件内的 [readFrom] 起算，第 [piece] 个分片能贡献多少**可读**字节。
 *
 * 三重钳制缺一不可：
 * - 片首 `piece * pieceBytes` —— [readFrom] 可能落在片的中间
 * - 片尾 `(piece + 1) * pieceBytes`
 * - 文件尾 [fileEnd] —— 最后一片通常不满
 *
 * ⚠️ [readFrom] 要传「**已经累计读到**的位置」，不是固定的起点：多片连续累加时，
 * 上一片的终点就是下一片的起点，这样每片只贡献一次、不会把已计入的字节重复算进去。
 */
internal fun pieceContribution(readFrom: Long, piece: Int, pieceBytes: Long, fileEnd: Long): Long {
    if (pieceBytes <= 0L) return 0L
    val pieceStart = piece.toLong() * pieceBytes
    val pieceEnd = min((piece.toLong() + 1) * pieceBytes, fileEnd)
    val from = max(readFrom, pieceStart)
    return (pieceEnd - from).coerceAtLeast(0L)
}

/**
 * 播放有了实际进展之后，之前记下的失败原因该不该清掉。
 *
 * [MagnetUnavailable.Stalled] 是**可恢复**的（带宽抖动，再给一次机会可能就来货了），
 * 所以一旦真的读出数据 / 起播成功，就说明它已经自愈 —— 留着会让「播成功」的场景
 * 也带着失败原因，诊断时被误导（属「把成功说成失败」那一类）。
 *
 * 终态原因（`BadMagnet` / `NoMetadata` / `NoVideoFile`）**不清**：重试也不会变好。
 */
internal fun shouldClearFailureOnProgress(failure: MagnetUnavailable?): Boolean =
    failure is MagnetUnavailable.Stalled
