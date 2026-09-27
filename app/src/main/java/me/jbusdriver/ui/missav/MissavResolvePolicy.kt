package me.jbusdriver.ui.missav

/** 搜索页解析一轮之后的处置动作。 */
internal enum class MissavResolveAction {
    /** 选出了可用资源，直接跳过去。 */
    PLAY,

    /** 页面是人机验证插页 —— 交给用户过一次验证，通过后重走整个解析流程（见 [decideChallengeOutcome]）。 */
    CHALLENGE,

    /** 候选还是 0，但列表可能是异步渲染还没出来 —— 再等一轮。 */
    RETRY,

    /** 站点确认没有这部片。 */
    NOT_FOUND,

    /**
     * 搜索页**根本没加载出来**（网络故障 / Cloudflare 5xx / 空壳页面）——
     * 既不是验证页，也**不能**算「没收录」。回退到站点页面，让用户看到真实页面并自行重试。
     */
    FALLBACK
}

/**
 * 判定「这看起来是一个真正的站点页面」的最小长度。
 *
 * 实测标定：站点**「无结果」**页也有 **252,977 字节**（title `Search result of ard-019ai - MissAV ...`）
 * —— 真实页面无论有没有搜索结果，都带着完整导航 / 页脚 / 自己的域名。
 * 而 Chromium 错误页、Cloudflare 5xx 错误页、`about:blank` 都远小于此。
 */
internal const val MISSAV_MIN_PAGE_LENGTH = 5000

/**
 * 页面看起来是不是**真正的站点页面**（而不是错误页 / 空壳）。
 *
 * 为什么需要它：`onReceivedError` 只覆盖**网络层**失败；Cloudflare 5xx 返回的是**正常响应**，
 * 不触发任何错误回调。两种情况下 `onPageFinished` 都会照常回调、解析出的候选都是 0，
 * 于是被判成 [MissavResolveAction.NOT_FOUND] —— 把「页面没加载出来」说成
 * 「站点没收录这部片」，与 UZU-040 是同一个用户可见症状。
 *
 * 判据刻意**偏保守**（宁可误判成「没加载出来」而回退站点，也不误判成「未收录」）：
 * 回退站点时用户看到的是站点的真实页面，比一句错误的「未收录」有用得多。
 */
internal fun looksLikeMissavPage(html: String): Boolean =
    html.length >= MISSAV_MIN_PAGE_LENGTH && html.contains("missav", ignoreCase = true)

internal data class MissavResolveDecision(
    val action: MissavResolveAction,
    /** 仅 [MissavResolveAction.PLAY] 有值。 */
    val url: String? = null,
    /** 解析出的候选数，只用于日志定位问题。 */
    val candidates: Int = 0
)

/** 搜索页最多解析几轮：列表是异步渲染的，第一轮可能只拿到空壳。 */
internal const val MISSAV_RESOLVE_ATTEMPTS = 2

/** 站点确实没有这部片时给用户看的文案。 */
internal const val MISSAV_NOT_FOUND_MESSAGE = "资源库还未收录，播放失败"

/**
 * 决定搜索页这一轮解析结果怎么处理。
 *
 * 顺序有讲究：**先解析、后判验证页**。反过来的话，带 Cloudflare JS Detections 的
 * 正常页面会被误判成验证页（判据说明见 [isMissavChallengeHtml]），
 * 于是明明搜到了结果却把人送去过验证。
 *
 * 「解析不出候选」有三种截然不同的原因，必须分开：
 * - 页面是验证插页 → [MissavResolveAction.CHALLENGE]，过一次验证就能继续；
 * - 页面正常但确实没有 → 先 [MissavResolveAction.RETRY] 再 [MissavResolveAction.NOT_FOUND]，
 *   直接判未收录会把「列表还没渲染完」误报成「资源库未收录」；
 * - 页面**根本没加载出来** → 先 [MissavResolveAction.RETRY] 再 [MissavResolveAction.FALLBACK]，
 *   判未收录等于把网络故障说成「站点没这部片」（判据见 [looksLikeMissavPage]）。
 */
internal fun decideSearchOutcome(
    html: String,
    code: String,
    attempt: Int,
    maxAttempts: Int = MISSAV_RESOLVE_ATTEMPTS
): MissavResolveDecision {
    val results = parseMissavSearchResults(html, code)
    selectBestMissavResult(results, code)?.let {
        return MissavResolveDecision(MissavResolveAction.PLAY, it.url, results.size)
    }
    if (isMissavChallengeHtml(html)) {
        return MissavResolveDecision(MissavResolveAction.CHALLENGE, candidates = results.size)
    }
    val retry = attempt + 1 < maxAttempts
    // ⚠️ 页面没加载出来时**不能**判「未收录」：那是把网络故障 / Cloudflare 5xx
    // 说成「站点没这部片」。重试完仍如此就回退站点，让用户看到真实页面。
    val exhausted = if (looksLikeMissavPage(html)) {
        MissavResolveAction.NOT_FOUND
    } else {
        MissavResolveAction.FALLBACK
    }
    return MissavResolveDecision(
        if (retry) MissavResolveAction.RETRY else exhausted,
        candidates = results.size
    )
}

/**
 * 人机验证是否已经通过 —— 判据是「当前页既不是验证 URL、标题也不像验证页」。
 *
 * 单独抽出来是因为它要同时看 URL 与标题，而标题只有 WebView 拿得到；
 * 抽成纯函数后这个判据能被单测覆盖。
 */
internal fun isChallengePassed(url: String?, title: String?): Boolean =
    !isMissavChallengeUrl(url) && !isMissavChallengeTitle(title)

/** 验证通过后最多自动重走几遍解析，防止「误判成已通过」时空转。 */
internal const val MISSAV_CHALLENGE_MAX_RESTARTS = 2

/**
 * 人机验证阶段收到一次**新文档标题**时，是否意味着「验证已经过了」——
 * 过了就该**立刻**把遮罩盖回去，而不是等页面加载完。
 *
 * **为什么不能等 `onPageFinished`**：Cloudflare 放行后会先跳回搜索页，搜索页**先渲染出来**，
 * 而 `onPageFinished` 要等整页（含全部子资源）加载完 —— 这段时间随网速变化，
 * 用户就一直盯着搜索结果页发呆，与「没触发人机验证」的体验不一致。
 * 实测（2026-09-24，ARSO-26210）：从「验证已通过」日志到第一次 `resolve` 之间隔了
 * **6 秒**，整段期间搜索页都是可见的。而标题在 `<head>` 里，解析出来得早得多。
 *
 * ⚠️ **判据不能只看 URL**（这是踩过的坑）：Cloudflare 的验证插页**就挂在搜索页自己的
 * URL 上**（URL 里既没有 `__cf_chl` 也没有 `cdn-cgi/challenge`），所以「URL 干净」完全
 * 不代表验证已过。实测只看 URL 会在「点完验证 → 插页再次下发」时连续误判，把重走预算
 * 白烧光，最后把用户甩到站点页面。
 *
 * ⚠️ 额外要求标题是**真的标题**：`onReceivedTitle` 在页面没有 `<title>` 时可能回传 URL
 * 甚至 null，那属于「不知道」，不能当成「已经过了」。这条守卫让失败方向只会是
 * 「慢一点」（退回 `onPageFinished` 兜底），而不会「判错」。
 */
internal fun shouldCoverOnTitle(url: String?, title: String?): Boolean {
    if (title.isNullOrBlank()) return false
    // 页面没有 <title> 时系统会把 URL 当标题回传 —— 那不是标题，别拿它当证据。
    if (title.equals(url, ignoreCase = true)) return false
    return isChallengePassed(url, title)
}

/** 停在验证页时的下一步。 */
internal enum class MissavChallengeOutcome {
    /** 还在验证页上 —— 继续等用户亲手过验证。 */
    WAIT,

    /** 验证已过 —— 重走一遍完整解析流程。 */
    RESTART,

    /** 反复验证后仍然拿不到正常页面 —— 回退站点，不再空转。 */
    GIVE_UP
}

/**
 * 人机验证通过之后要不要「重走」解析流程。
 *
 * 为什么是重走、而不是在原地续跑：
 * 验证通过后站点把我们带到哪一页是**不确定的** —— 搜索页、站点自己的播放页、
 * 首页、带参数的重定向都有可能。原地续跑就必须把每一种落点都处理对，
 * 漏掉一种就是**静默卡死**：界面停在「正在解析播放地址…」，
 * 最后被解析超时兜底甩回站点页面 —— 而那时站点播放器往往已经自己播起来了，
 * 用户看到的就是「验证完却跳去了网页」。
 *
 * 重走一遍则只有一条路：重新加载搜索页 → 解析 → 选片 → 播放页探测。
 * 这条路和「已认证后再次播放」走的是**同一段代码**，所以两种体验天然一致。
 *
 * [restarts] 是已经重走过的次数：验证判据是「当前页不像验证页」这种**否定式**判断，
 * 万一被误判成已通过，重走会再次撞上验证页；给个上限，超了就老老实实回退站点。
 */
internal fun decideChallengeOutcome(
    passed: Boolean,
    restarts: Int,
    maxRestarts: Int = MISSAV_CHALLENGE_MAX_RESTARTS
): MissavChallengeOutcome = when {
    !passed -> MissavChallengeOutcome.WAIT
    restarts < maxRestarts -> MissavChallengeOutcome.RESTART
    else -> MissavChallengeOutcome.GIVE_UP
}
