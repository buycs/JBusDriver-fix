package me.jbusdriver.mvp.bean

import me.jbusdriver.common.bean.ILink
import me.jbusdriver.common.bean.db.ForumCategory

/**
 * 論壇相关的展示模型。全部只读: 论坛只做浏览, 不做回复/发帖。
 */

/**
 * 論壇固定入口。帖子页自带 <base href="https://www.javbus.com/forum/">,
 * 页面里的 forum.php / data/attachment 相对路径都以它为根
 */
const val FORUM_SITE_HOST = "https://www.javbus.com"
const val FORUM_HOME_URL = "$FORUM_SITE_HOST/forum/"

data class ForumPost(val name: String, val image: String, override val link: String) : ILink {
    /**
     * 帖子归到「默认帖子分类」(7), 不是链接分类(10)。
     * convertDBItem() 里 `categoryId > 0` 这一支先命中, 所以这个默认值就是最终落库的分类 ——
     * 写成 LinkCategory 的话, 帖子会被塞进链接分类, 帖子收藏页永远查不到。
     */
    @Transient
    override var categoryId: Int = ForumCategory.id ?: 7

    val tid: String
        get() = Regex("tid=(\\d+)").find(link)?.groupValues?.getOrNull(1).orEmpty()
}

/**
 * 帖子正文里的一段, 顺序即站内顺序
 */
sealed class ForumSegment {
    /** 一段连续文本, 已还原换行; 文内链接以 URLSpan 形式带在里面 */
    data class Text(val content: CharSequence) : ForumSegment()

    /** 引用回复: header 形如 "某人 发表于 2026-9-25 10:00" */
    data class Quote(val header: String, val content: CharSequence) : ForumSegment()

    /**
     * 图片: thumb 用于列表内嵌显示, full 用于大图查看;
     * imageIndex 是这条图在所属楼层 images 里的下标, 点图时直接带着它跳查看器
     */
    data class Image(val thumb: String, val full: String, val imageIndex: Int) : ForumSegment()
}

data class ForumFloor(
    val floorNo: String,
    val author: String,
    val time: String,
    val segments: List<ForumSegment>,
    val images: List<String>
)

data class ForumThreadPost(
    val title: String,
    val floors: List<ForumFloor>,
    val hasNext: Boolean = false,
    val nextUrl: String = "",
    val pageText: String = "",
    val page: Int = 1,
    val totalPage: Int = 1
)

data class ForumThreadSummary(
    val title: String,
    val url: String,
    val board: String,
    val author: String,
    val time: String,
    val views: String,
    val replies: String
)

data class ForumThreadList(
    val title: String,
    val threads: List<ForumThreadSummary>,
    val hasNext: Boolean = false,
    val nextUrl: String = "",
    val pageText: String = "",
    /** 板塊页顶部展示区: 最新主題/精選內容/精選主題三栏, 内容内嵌在列表页 HTML 里 */
    val hotTabs: List<ForumHotTab> = emptyList(),
    /** 排序行(最新/熱門/熱帖/精華)与主题分类行(全部/日本/韓國...) */
    val sortOptions: List<ForumOption> = emptyList(),
    val filterOptions: List<ForumOption> = emptyList()
)

/**
 * 論壇里一个可点开的列表入口: 板塊或分類, desc 是站点给的一句话简介
 */
data class ForumEntry(val name: String, val url: String, val desc: String = "")

data class ForumBoardGroup(val name: String, val boards: List<ForumEntry>)

/** 首页左轮播: 一张 365x290 的封面 + 标题, 点开是帖子 */
data class ForumSlide(val title: String, val image: String, val link: String)

/** 首页右上热帖: 站方列表里带楼主, 但界面只展示标题 */
data class ForumHotItem(val title: String, val link: String)

/**
 * 站点顶栏里一个可点的筛选/排序项。selected 的取法两行不一样:
 * 分类行站点把当前项标在 li 的 class 上, 排序行不标, 只能拿跳转地址跟本页地址比。
 */
data class ForumOption(val label: String, val url: String, val selected: Boolean = false)

/**
 * Discuz 里 tid 才是帖子的唯一键: 同一个帖在首页不同区块里的 href 可能带不同的额外参数,
 * 用整条 url 去重会把同帖留下两份。tid 取不到时退回 url, 至少不会把所有空值并成一条。
 */
fun forumTidOf(url: String): String =
    Regex("""tid=(\d+)""").find(url)?.groupValues?.getOrNull(1).orEmpty().ifBlank { url }

/** 首页右热帖的一个页签, 内容已经内嵌在首页 HTML 里 */
data class ForumHotTab(val name: String, val items: List<ForumHotItem>)

data class ForumHome(
    val slides: List<ForumSlide>,
    val hotTabs: List<ForumHotTab>,
    val groups: List<ForumBoardGroup>
)
