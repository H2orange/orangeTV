package top.orangetv.data.model

/** 分类页排序方式 */
enum class SortOrder {
    /** 最新 */
    Latest,

    /** 评分 */
    Score,

    /** 年份 */
    Year
}

/**
 * 点播条目（列表态）
 *
 * [initials] 是拼音首字母索引，由数据源或入库流程预先算好，
 * 保证遥控器输入 TKZC 能直接命中「天空之城」。
 */
data class VodSummary(
    val id: String,
    val title: String,
    val cover: String? = null,
    val score: Float? = null,
    val year: Int? = null,
    val region: String? = null,
    val category: String? = null,
    val remarks: String? = null,
    val initials: String = ""
)

/**
 * 数据源提供的分类项。
 *
 * 之前分类栏是写死的「电影/剧集/综艺/动漫」，对苹果 CMS 这类源不成立 ——
 * 它们的分类是「动作片/国产剧/欧美剧」这种更细的二级分类，
 * 而且一级分类（电影片/连续剧）用 t= 查会返回 0 条。
 * 所以改成由源自己提供分类表，UI 照着渲染。
 */
data class VodCategory(
    val id: String,
    val name: String
)

/** 首页一行内容 */
data class VodRow(
    val key: String,
    val title: String,
    val items: List<VodSummary>,
    val wide: Boolean = false
)

/** 一条播放线路 */
data class PlayLine(
    val name: String,
    val episodes: List<Episode>
)

data class Episode(
    val index: Int,
    val name: String
)

/** 点播详情 */
data class VodDetail(
    val summary: VodSummary,
    val description: String = "",
    val director: String? = null,
    val actors: String? = null,
    val lines: List<PlayLine> = emptyList()
) {
    val firstLine: PlayLine? get() = lines.firstOrNull()
}

/** 直播频道 */
data class Channel(
    val id: String,
    val name: String,
    val group: String,
    val logo: String? = null,
    val url: String,
    val sourceId: String = ""
)

/** 节目单 */
data class EpgProgram(
    val title: String,
    val startMillis: Long,
    val endMillis: Long
) {
    fun progress(now: Long = System.currentTimeMillis()): Float {
        if (endMillis <= startMillis) return 0f
        return ((now - startMillis).toFloat() / (endMillis - startMillis)).coerceIn(0f, 1f)
    }
}
