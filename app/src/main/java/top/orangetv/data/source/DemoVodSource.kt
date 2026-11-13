package top.orangetv.data.source

import kotlinx.coroutines.delay
import top.orangetv.data.model.Episode
import top.orangetv.data.model.PlayLine
import top.orangetv.data.model.SortOrder
import top.orangetv.data.model.VodDetail
import top.orangetv.data.model.VodRow
import top.orangetv.data.model.VodSummary
import top.orangetv.util.HanziInitials

/**
 * 内置演示源：不需要任何配置，装上就能看到完整界面。
 *
 * 用途是验证 UI 与交互，标题均为虚构，播放地址用公开测试流。
 * 接入真实数据源时，照着 [VodSource] 实现一个类即可，UI 不用改。
 */
class DemoVodSource : VodSource {

    override val id = "demo"
    override val displayName = "内置演示源"
    override val priority = 999

    override suspend fun probe(): ProbeResult {
        delay(60)
        return ProbeResult.Ok(60)
    }

    private val data: List<VodSummary> = listOf(
        item("1", "深空回声", 8.9f, 2026, "大陆", "剧集", "全12集"),
        item("2", "雾港谜案", 8.4f, 2026, "中国香港", "剧集", "更新至8"),
        item("3", "午夜频率", 8.2f, 2025, "韩国", "剧集", "全16集"),
        item("4", "第七号档案", 8.6f, 2025, "欧美", "剧集", "全24集"),
        item("5", "静默之海", 8.7f, 2024, "日本", "剧集", "全10集"),
        item("6", "三体边界", 9.2f, 2026, "大陆", "剧集", "全12集"),
        item("7", "天空之城", 9.1f, 1986, "日本", "电影", null),
        item("8", "荒原列车", 8.0f, 2023, "欧美", "电影", null),
        item("9", "光年之外", 8.3f, 2026, "大陆", "电影", null),
        item("10", "锈色黎明", 7.8f, 2025, "欧美", "电影", null),
        item("11", "长夜将尽", 9.4f, 2025, "大陆", "电影", null),
        item("12", "归途列车", 9.2f, 2024, "大陆", "电影", null),
        item("13", "雪线以上", 8.5f, 2024, "大陆", "电影", null),
        item("14", "折叠城市", 9.0f, 2026, "大陆", "动漫", "更新至6"),
        item("15", "星际信使", 9.1f, 2026, "日本", "动漫", "更新至18"),
        item("16", "风起时", 8.6f, 2026, "大陆", "综艺", "更新至9"),
        item("17", "春日慢行", 8.8f, 2025, "大陆", "综艺", "全12期"),
        item("18", "雾中灯塔", 7.6f, 2026, "大陆", "短剧", "全68集"),
        item("19", "重逢之前", 7.9f, 2026, "大陆", "短剧", "全80集"),
        item("20", "玻璃海岸", 8.2f, 2025, "中国台湾", "剧集", "全20集")
    )

    private fun item(
        id: String,
        title: String,
        score: Float,
        year: Int,
        region: String,
        category: String,
        remarks: String?
    ) = VodSummary(
        id = id,
        title = title,
        score = score,
        year = year,
        region = region,
        category = category,
        remarks = remarks,
        initials = HanziInitials.of(title)
    )

    override suspend fun rows(): List<VodRow> {
        delay(120)
        return listOf(
            VodRow("hot", "热门推荐", data.filter { it.category == "剧集" }),
            VodRow("new", "新剧上线", data.filter { (it.year ?: 0) >= 2025 }.shuffled()),
            VodRow("movie", "电影精选", data.filter { it.category == "电影" }),
            VodRow("anime", "动漫番剧", data.filter { it.category == "动漫" || it.category == "综艺" })
        )
    }

    override suspend fun browse(
        category: String?,
        region: String?,
        year: String?,
        sort: SortOrder,
        page: Int
    ): List<VodSummary> {
        delay(80)
        var list = data.filter { item ->
            (category == null || item.category == category) &&
                (region == null || item.region == region) &&
                (year == null || item.year.toString() == year)
        }
        list = when (sort) {
            SortOrder.Latest -> list.sortedByDescending { it.year ?: 0 }
            SortOrder.Score -> list.sortedByDescending { it.score ?: 0f }
            SortOrder.Year -> list.sortedByDescending { it.year ?: 0 }
        }
        return list
    }

    override suspend fun search(keyword: String, page: Int): List<VodSummary> {
        delay(60)
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()
        val upper = q.uppercase()
        return data.filter {
            it.title.contains(q) || (it.initials.isNotEmpty() && it.initials.contains(upper))
        }
    }

    override suspend fun detail(id: String): VodDetail {
        delay(80)
        val s = data.firstOrNull { it.id == id } ?: data.first()
        val count = s.remarks?.filter { it.isDigit() }?.toIntOrNull() ?: 12
        return VodDetail(
            summary = s,
            description = "这部作品围绕一场意外展开，人物在有限的时间里被迫做出选择。" +
                "叙事克制、影像干净，结尾留白处理得相当漂亮。",
            director = "—",
            actors = "—",
            lines = listOf(
                PlayLine(
                    name = "线路一",
                    episodes = (1..count.coerceAtMost(24)).map { Episode(it, "第 $it 集") }
                )
            )
        )
    }

    override suspend fun playUrl(vodId: String, lineIndex: Int, episodeIndex: Int): String =
        // 公开测试流，仅用于验证播放链路
        "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
}
