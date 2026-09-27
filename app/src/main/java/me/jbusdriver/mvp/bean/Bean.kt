package me.jbusdriver.mvp.bean

import com.google.gson.annotations.SerializedName
import me.jbusdriver.base.toJsonString
import me.jbusdriver.base.urlPath
import me.jbusdriver.common.bean.ICollectCategory
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.common.bean.db.AllFirstParentDBCategoryGroup
import me.jbusdriver.common.bean.db.LinkCategory
import me.jbusdriver.db.bean.LinkItem
import me.jbusdriver.http.BUS_SITE
import me.jbusdriver.ui.data.enums.SearchType
import java.util.*

/**
 * Created by Administrator on 2017/4/9.
 */

const val Expand_Type_Head = 0
const val Expand_Type_Item = 1


val ILink.des: String
    inline get() = when (this) {
        is Header -> "$name $value"
        is Genre -> "类别 $name"
        is ActressInfo -> "演员 $name"
        is me.jbusdriver.mvp.bean.Movie -> "$code $title"
        is SearchLink -> "搜索 ${type.title} $query"
        is PageLink -> "$title 第 $page 页" /*${if (isAll) "全部" else "已有种子"}电影*/
        is ForumPost -> "帖子 $name"
        else -> error(" $this has no matched class for des")
    }

const val MovieDBType = 1
const val ActressDBType = 2
const val HeaderDBType = 3
const val GenreDBType = 4
const val SearchLinkDBType = 5
const val PageLinkDBType = 6

/** 論壇帖子收藏。3..9 是 Category.kt 里预留的号段, 帖子取 7 */
const val ForumPostDBType = 7

val AllDBType by lazy {
    listOf(
        MovieDBType,
        ActressDBType,
        HeaderDBType,
        GenreDBType,
        SearchLinkDBType,
        PageLinkDBType,
        ForumPostDBType
    )
}

val ILink.DBtype: Int
    inline get() = when (this) {
        is Movie -> MovieDBType
        is ActressInfo -> ActressDBType
        is Header -> HeaderDBType
        is Genre -> GenreDBType
        is SearchLink -> SearchLinkDBType
        is PageLink -> PageLinkDBType
        is ForumPost -> ForumPostDBType
        else -> error(" $this has no matched class for des")
    }
val ILink.uniqueKey: String
    inline get() = when (this) {
        is SearchLink -> query
        // 帖子地址都是 /forum/forum.php?mod=viewthread&tid=xxx, urlPath 取出来全一样,
        // 必须用 tid 当键 —— 否则收藏第二个帖子会覆盖第一个
        is ForumPost -> "forum-${tid}"
        else -> link.urlPath
    }

fun ILink.convertDBItem() = LinkItem(
    this.DBtype, Date(), this.uniqueKey, this.toJsonString(),
    when {
        this is ICollectCategory && this.categoryId > 0 -> categoryId
        else -> AllFirstParentDBCategoryGroup[this.DBtype]?.id ?: LinkCategory.id ?: -1
    }
)

data class PageLink(val page: Int, val title: String /*XX类型*/, override val link: String) : ILink {
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
}


data class SearchLink(val type: SearchType, var query: String) : ILink {
    @Transient
    override var categoryId: Int = LinkCategory.id ?: 10
    override val link: String
        get() = "$BUS_SITE${type.urlPathFormater.format(query)}"

}


/** 远端 properties.json 里跟版本有关的三个字段, 下载地址固定跳 releases 页 */
data class UpdateBean(
    @SerializedName("latest_version_code") val versionCode: Int = 0,
    @SerializedName("latest_version") val versionName: String? = null,
    @SerializedName("changelog") val changelog: String? = null
)


