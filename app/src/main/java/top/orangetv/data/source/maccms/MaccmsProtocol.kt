package top.orangetv.data.source.maccms

import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder

/**
 * 苹果 CMS v10 采集协议（maccms）。
 *
 * 电影天堂、厂长以及绝大多数资源站都基于这一套，接口形态一致：
 *   GET <base>/api.php/provide/vod/?ac=detail&pg=1&t=<分类>&wd=<关键词>&ids=<id列表>
 *
 * 两个必须知道的行为（都实测过）：
 * 1. **`pg` 必须带**。不带 pg 时服务端会忽略 t/area/year 等过滤条件，直接返回全量。
 * 2. **`by` 排序参数多数站点不生效**，排序只能拿到数据后在本地做。
 *
 * 返回格式有 JSON 和 XML 两种。电影天堂帮助页给的是 `/at/xml/`，
 * 但 JSON 更省事，所以这里把两种都吃掉，按响应首字符自动判别。
 */
object MaccmsUrl {

    /**
     * 把用户填的任意形态归一化成 JSON 接口基址。
     *
     * 支持输入：
     *   http://caiji.dyttzyapi.com/api.php/provide/vod/at/xml/
     *   https://caiji.dyttzyapi.com/api.php/provide/vod
     *   http://caiji.dyttzyapi.com
     *   caiji.dyttzyapi.com/api.php/provide/vod/
     *
     * 输出统一为：`scheme://host/api.php/provide/vod`
     * （去掉 /at/xml 后缀 —— 那只是格式装饰，去掉后默认就是 JSON）
     */
    fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"

        val schemeEnd = s.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = s.substring(0, schemeEnd).lowercase()

        var rest = s.substring(schemeEnd + 3)
        rest = rest.substringBefore('#').substringBefore('?')
        val slash = rest.indexOf('/')
        val host = if (slash >= 0) rest.substring(0, slash) else rest
        if (host.isBlank()) return null

        var path = if (slash >= 0) rest.substring(slash) else ""
        path = path.trimEnd('/')
        // 格式后缀：/at/xml、/at/json 只是输出格式开关，去掉
        path = path.replace(Regex("/at/(xml|json)/?$", RegexOption.IGNORE_CASE), "").trimEnd('/')

        path = when {
            path.endsWith("/api.php", true) -> "/api.php/provide/vod"
            path.contains("api.php", true) -> path
            else -> "/api.php/provide/vod"
        }
        return "$scheme://$host$path"
    }

    /** XML 输出地址：分类字典（<class>）只有 XML 形态才给，JSON 没有 */
    fun xmlBase(apiBase: String): String = apiBase.trimEnd('/') + "/at/xml/"

    fun buildUrl(apiBase: String, params: List<Pair<String, Any?>>): String {
        val query = params
            .filter { it.second != null && it.second.toString().isNotBlank() }
            .joinToString("&") { "${it.first}=${URLEncoder.encode(it.second.toString(), "UTF-8")}" }
        return if (query.isEmpty()) apiBase else "$apiBase?$query"
    }
}

/** 分类字典项 */
data class MaccmsClass(val id: String, val name: String)

data class MaccmsEpisode(val name: String, val url: String)

/** 一条播放线路：flag 是线路名（dytt / dyttm3u8 / ...） */
data class MaccmsLine(val flag: String, val episodes: List<MaccmsEpisode>)

data class MaccmsVod(
    val id: String,
    val name: String,
    /** vod_en：片名全拼（如 zashidongwu），用于拼音/首字母本地检索 */
    val en: String? = null,
    val typeId: String,
    val typeName: String,
    val pic: String?,
    val year: Int?,
    val area: String?,
    val remarks: String?,
    val score: Float?,
    val actor: String?,
    val director: String?,
    val description: String?,
    val lines: List<MaccmsLine>
)

data class MaccmsPage(
    val items: List<MaccmsVod> = emptyList(),
    val total: Int = 0,
    val pageCount: Int = 0,
    val classes: List<MaccmsClass> = emptyList(),
    /** 服务端 code=1 或 XML 解析成功即为 true，用来区分"空结果"和"请求失败" */
    val ok: Boolean = false
)

object MaccmsParser {

    fun parse(body: String): MaccmsPage {
        val trimmed = body.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (trimmed.isEmpty()) return MaccmsPage()
        return runCatching {
            if (trimmed.startsWith("<")) parseXml(trimmed) else parseJson(trimmed)
        }.getOrElse { MaccmsPage() }
    }

    /* ---------------- JSON ---------------- */

    private fun parseJson(body: String): MaccmsPage {
        val root = JSONObject(body)
        val code = root.optInt("code", 0)
        val arr = root.optJSONArray("list") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { jsonVod(it) }
        }
        val classes = root.optJSONArray("class")?.let { ca ->
            (0 until ca.length()).mapNotNull { i ->
                val o = ca.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("type_id").ifBlank { o.optString("id") }
                val name = o.optString("type_name").ifBlank { o.optString("name") }
                if (id.isBlank() || name.isBlank()) null else MaccmsClass(id, name)
            }
        } ?: emptyList()
        return MaccmsPage(
            items = items,
            total = root.optInt("total", 0),
            pageCount = root.optInt("pagecount", 0),
            classes = classes,
            ok = code == 1
        )
    }

    private fun jsonVod(o: JSONObject): MaccmsVod? {
        val id = o.optString("vod_id").trim()
        if (id.isBlank()) return null
        val from = o.optString("vod_play_from")
        val play = o.optString("vod_play_url")
        val flags = splitFlags(from)
        val groups = if (play.contains("$$$")) play.split("$$$") else listOf(play)
        return MaccmsVod(
            id = id,
            name = o.optString("vod_name").trim(),
            en = o.optString("vod_en").trim().takeIf { it.isNotBlank() },
            typeId = o.optString("type_id").trim(),
            typeName = o.optString("type_name").trim(),
            pic = o.optString("vod_pic").trim().takeIf { it.startsWith("http") },
            year = o.optString("vod_year").trim().toIntOrNull(),
            area = o.optString("vod_area").clean(),
            remarks = o.optString("vod_remarks").clean(),
            score = o.optString("vod_score").trim().toFloatOrNull()?.takeIf { it > 0f },
            actor = o.optString("vod_actor").clean(),
            director = o.optString("vod_director").clean(),
            description = o.optString("vod_content").clean(),
            lines = buildLines(flags, groups)
        )
    }

    /* ---------------- XML（RSS 5.1） ---------------- */

    private val TEXT_FIELDS = setOf(
        "id", "tid", "name", "type", "pic", "lang", "area", "year",
        "note", "actor", "director", "des", "state", "subname", "last"
    )

    private class XmlVod {
        val fields = mutableMapOf<String, String>()
        val play = mutableListOf<Pair<String, String>>()   // flag -> 播放串

        fun toVod(): MaccmsVod {
            val flags = play.map { it.first }
            val groups = play.map { it.second }
            return MaccmsVod(
                id = fields["id"].orEmpty().trim(),
                name = fields["name"].orEmpty().trim(),
                typeId = fields["tid"].orEmpty().trim(),
                typeName = fields["type"].orEmpty().trim(),
                pic = fields["pic"].clean()?.takeIf { it.startsWith("http") },
                year = fields["year"].orEmpty().trim().toIntOrNull(),
                area = fields["area"].clean(),
                remarks = fields["note"].clean(),
                // XML 输出不带评分字段
                score = null,
                actor = fields["actor"].clean(),
                director = fields["director"].clean(),
                description = fields["des"].clean(),
                lines = buildLines(flags, groups)
            )
        }
    }

    private fun parseXml(body: String): MaccmsPage {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(body))

        val items = mutableListOf<MaccmsVod>()
        val classes = mutableListOf<MaccmsClass>()
        var total = 0
        var pageCount = 0
        var current: XmlVod? = null
        var pendingField: String? = null
        var pendingFlag: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (val tag = parser.name?.lowercase().orEmpty()) {
                    "list" -> {
                        total = parser.getAttributeValue(null, "recordcount")?.toIntOrNull() ?: 0
                        pageCount = parser.getAttributeValue(null, "pagecount")?.toIntOrNull() ?: 0
                    }

                    "video" -> current = XmlVod()

                    "ty" -> {
                        // 分类字典，readText 会把解析器推到 </ty>
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val name = readText(parser).trim()
                        if (id.isNotBlank() && name.isNotBlank()) classes += MaccmsClass(id, name)
                    }

                    "dd" -> {
                        pendingField = "dd"
                        pendingFlag = parser.getAttributeValue(null, "flag").orEmpty()
                    }

                    in TEXT_FIELDS -> pendingField = tag

                    else -> Unit
                }

                XmlPullParser.TEXT -> {
                    val text = parser.text.orEmpty()
                    val vod = current
                    val field = pendingField
                    if (vod != null && field != null) {
                        if (field == "dd") vod.play += pendingFlag.orEmpty() to text
                        else vod.fields[field] = text
                    }
                    pendingField = null
                }

                XmlPullParser.END_TAG -> {
                    pendingField = null
                    if (parser.name.equals("video", true)) {
                        current?.let { v -> v.toVod().takeIf { it.id.isNotBlank() }?.let(items::add) }
                        current = null
                    }
                }
            }
            event = parser.next()
        }

        return MaccmsPage(
            items = items,
            total = total,
            pageCount = pageCount,
            classes = classes,
            ok = true
        )
    }

    /** 读到当前标签的结束标签为止，返回其中所有文本（含 CDATA） */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                // next() 会把 CDATA 段落并进 TEXT 事件，不用单独处理
                XmlPullParser.TEXT -> sb.append(parser.text.orEmpty())
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
        return sb.toString()
    }

    /* ---------------- 公共拼装 ---------------- */

    /**
     * 线路名拆分。detail 用 `$$$` 分隔；轻量列表里可能退化成 `dytt,dyttm3u8`。
     */
    private fun splitFlags(from: String): List<String> = when {
        from.isBlank() -> emptyList()
        from.contains("$$$") -> from.split("$$$")
        from.contains(",") -> from.split(",")
        else -> listOf(from)
    }.map { it.trim() }

    /**
     * 把 flag 与播放串按位对齐。
     * 播放串格式：`第01集$http://...#第02集$http://...`
     */
    private fun buildLines(flags: List<String>, groups: List<String>): List<MaccmsLine> {
        val size = maxOf(flags.size, groups.size)
        return (0 until size).mapNotNull { i ->
            val raw = groups.getOrNull(i).orEmpty()
            if (raw.isBlank()) return@mapNotNull null
            val episodes = raw.split("#").mapIndexedNotNull { j, segment ->
                val seg = segment.trim()
                when {
                    seg.isEmpty() -> null
                    // 少数源不带「集名$地址」，直接甩一条裸地址
                    seg.startsWith("http") && !seg.contains('$') ->
                        MaccmsEpisode("第 ${j + 1} 集", seg)
                    else -> {
                        val sep = seg.indexOf('$')
                        if (sep <= 0) null
                        else MaccmsEpisode(seg.substring(0, sep).trim(), seg.substring(sep + 1).trim())
                    }
                }
            }.filter { it.url.isNotBlank() }
            if (episodes.isEmpty()) null
            else MaccmsLine(flags.getOrNull(i)?.ifBlank { "线路 ${i + 1}" } ?: "线路 ${i + 1}", episodes)
        }
    }

    private fun String?.clean(): String? {
        if (this == null) return null
        if (isBlank()) return null
        var s = replace(Regex("<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        s = s.replace(Regex("[\\s\\u3000]+"), " ").trim()
        return s.ifBlank { null }
    }
}
