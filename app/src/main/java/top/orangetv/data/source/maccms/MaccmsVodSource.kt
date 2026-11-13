package top.orangetv.data.source.maccms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import top.orangetv.data.model.Episode
import top.orangetv.data.model.PlayLine
import top.orangetv.data.model.SortOrder
import top.orangetv.data.model.VodCategory
import top.orangetv.data.model.VodDetail
import top.orangetv.data.model.VodRow
import top.orangetv.data.model.VodSummary
import top.orangetv.data.source.ProbeResult
import top.orangetv.data.source.VodSource
import top.orangetv.util.HanziInitials
import java.util.concurrent.ConcurrentHashMap

/**
 * 苹果 CMS（maccms v10）点播源。
 *
 * 这是「协议适配器」：地址由用户填，App 不内置、不发现任何站点。
 * 电影天堂、厂长、以及绝大多数资源站都是同一套接口，所以都能接。
 *
 * 实测踩到的坑（都已在实现里处理）：
 * 1. `/at/xml/` 只是输出格式开关，去掉后默认 JSON。这里统一走 JSON，
 *    但解析器同时认 XML —— 万一某个站点只给 XML 也能跑。
 * 2. **`pg` 必须带**。缺了 pg，`t` / `area` / `year` 全部失效，直接返回全量。
 * 3. `by` 排序参数不生效，只能在本地排。
 * 4. 线路要挑：`dytt` 是分享页（HTML，播放器打不开），`dyttm3u8` 才是直链。
 *    规则是「地址后缀命中 .m3u8/.mp4 的线路优先，含 /share/ 的排最后」。
 * 5. 列表页 `ac=list` 只有 8 个字段、没有封面图，所以卡片数据必须走 `ac=detail`
 *    （20 条约 500KB，换来封面/评分/简介，只能接受）。
 */
class MaccmsVodSource(
    override val id: String,
    override val displayName: String,
    override val priority: Int,
    rawUrl: String,
    private val client: OkHttpClient
) : VodSource {

    /** 归一化后的 JSON 接口基址，形如 http://host/api.php/provide/vod */
    val apiBase: String = MaccmsUrl.normalize(rawUrl) ?: rawUrl.trim()

    /** 完整数据（带播放地址），key = vod_id */
    private val fullCache = ConcurrentHashMap<String, MaccmsVod>()

    /** 首字母索引，覆盖「看过的 + 预热拉取的」条目 */
    private val index = ConcurrentHashMap<String, IndexEntry>()

    @Volatile
    private var classCache: List<MaccmsClass>? = null

    private data class IndexEntry(
        val id: String,
        val name: String,
        val initials: String,
        val pinyin: String?
    )

    /* ==================== 探活 ==================== */

    override suspend fun probe(): ProbeResult = MaccmsProbe.check(client, apiBase)

    /* ==================== 分类 ==================== */

    override suspend fun categories(): List<VodCategory> =
        loadClasses().map { VodCategory(id = it.id, name = it.name) }

    /** 分类字典只在 XML 输出里有（`<class><ty id="6">动作片</ty>`），JSON 没有 */
    private suspend fun loadClasses(): List<MaccmsClass> {
        classCache?.let { return it }
        val page = withTimeoutOrNull(CLASS_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = MaccmsUrl.buildUrl(
                        MaccmsUrl.xmlBase(apiBase),
                        listOf("ac" to "list", "pg" to 1)
                    )
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        if (!resp.isSuccessful) MaccmsPage()
                        else MaccmsParser.parse(resp.body?.string().orEmpty())
                    }
                }.getOrElse { MaccmsPage() }
            }
        } ?: MaccmsPage()

        val resolved = page.classes.ifEmpty { deriveClasses() }
        if (resolved.isNotEmpty()) classCache = resolved
        return resolved
    }

    /** 拿不到字典时，用已经见过的条目反推，聊胜于无 */
    private fun deriveClasses(): List<MaccmsClass> =
        fullCache.values
            .mapNotNull { v ->
                v.typeId.takeIf { it.isNotBlank() && v.typeName.isNotBlank() }
                    ?.let { MaccmsClass(it, v.typeName) }
            }
            .distinctBy { it.id }

    private suspend fun resolveTid(category: String?): String? {
        val c = category?.trim().orEmpty()
        if (c.isEmpty() || c == "全部") return null
        if (c.all { it.isDigit() }) return c
        return loadClasses().firstOrNull { it.name == c }?.id
    }

    /* ==================== 首页 ==================== */

    override suspend fun rows(): List<VodRow> = coroutineScope {
        val classes = runCatching { loadClasses() }.getOrDefault(emptyList())

        // 一级分类（电影片/连续剧）在苹果 CMS 里 t= 只匹配自身、不带子类，
        // 实测 t=1 返回 0 条，所以首页行只能用二级分类精确命中。
        val specs = mutableListOf<Triple<String, String, String?>>(
            Triple("latest", "最新上线", null)
        )
        listOf("国产剧", "动作片", "国产动漫", "大陆综艺").forEach { name ->
            classes.firstOrNull { it.name == name }?.let { specs += Triple(it.id, it.name, it.id) }
        }

        specs.map { (key, title, tid) ->
            async {
                val page = fetch(listOf("ac" to "detail", "pg" to 1) + listOfNotNull(tid?.let { "t" to it }))
                VodRow(
                    key = key,
                    title = title,
                    items = page.items.map { it.toSummary() }.distinctBy { it.id }
                )
            }
        }.awaitAll().filter { it.items.isNotEmpty() }
    }

    /* ==================== 浏览 ==================== */

    override suspend fun browse(
        category: String?,
        region: String?,
        year: String?,
        sort: SortOrder,
        page: Int
    ): List<VodSummary> {
        val tid = resolveTid(category)
        // 选了分类却解析不出 id：宁可返回空，也不要给用户一堆不相干的内容
        if (!category.isNullOrBlank() && category != "全部" && tid == null) return emptyList()

        val params = mutableListOf<Pair<String, Any?>>("ac" to "detail", "pg" to page.coerceAtLeast(1))
        tid?.let { params += "t" to it }
        MaccmsRegion.map(region)?.let { params += "area" to it }
        year?.takeIf { it.isNotBlank() && it != "全部" }?.let { params += "year" to it }

        val list = fetch(params).items.map { it.toSummary() }
        return when (sort) {
            // 服务端默认按更新时间倒序，保持原样
            SortOrder.Latest -> list
            SortOrder.Score -> list.sortedByDescending { it.score ?: 0f }
            SortOrder.Year -> list.sortedByDescending { it.year ?: 0 }
        }
    }

    /* ==================== 搜索 ==================== */

    /**
     * 中文关键词走服务端 `wd=`（苹果 CMS 只匹配片名）。
     * 纯字母（首字母/拼音）服务端搜不到，退化为本地索引匹配 ——
     * 覆盖范围是「已看过的 + 启动时预热的前几页」，不是全库。
     */
    override suspend fun search(keyword: String, page: Int): List<VodSummary> {
        val q = keyword.trim()
        if (q.isEmpty()) return emptyList()

        if (q.any { it.code > 0x2E7F }) {
            return fetch(listOf("ac" to "detail", "pg" to page.coerceAtLeast(1), "wd" to q))
                .items.map { it.toSummary() }
        }

        val key = q.uppercase()
        val lower = q.lowercase()
        ensureIndex()
        val ids = index.values
            .filter { e ->
                (e.initials.isNotEmpty() && e.initials.startsWith(key)) ||
                    (e.pinyin?.startsWith(lower) == true)
            }
            .sortedByDescending { it.id.toIntOrNull() ?: 0 }
            .map { it.id }
            .distinct()
            .take(MAX_SEARCH_HITS)

        if (ids.isEmpty()) return emptyList()
        return fetchByIds(ids).map { it.toSummary() }
    }

    /** 索引太小就先预热几页轻量列表（每页 6KB，比 detail 便宜 80 倍） */
    private suspend fun ensureIndex() {
        if (index.size >= INDEX_MIN) return
        coroutineScope {
            (1..INDEX_PAGES).map { p ->
                async { fetch(listOf("ac" to "list", "pg" to p)) }
            }.awaitAll()
        }
    }

    /* ==================== 详情 / 播放 ==================== */

    override suspend fun detail(vodId: String): VodDetail {
        val vod = loadFull(vodId) ?: error("未找到该影片")
        return VodDetail(
            summary = vod.toSummary(),
            description = vod.description.orEmpty(),
            director = vod.director,
            actors = vod.actor,
            lines = vod.lines.map { line ->
                PlayLine(
                    name = line.flag,
                    episodes = line.episodes.mapIndexed { i, e -> Episode(index = i, name = e.name) }
                )
            }
        )
    }

    override suspend fun playUrl(vodId: String, lineIndex: Int, episodeIndex: Int): String {
        val vod = loadFull(vodId) ?: return ""
        val line = vod.lines.getOrNull(lineIndex) ?: vod.lines.firstOrNull() ?: return ""
        val ep = line.episodes.getOrNull(episodeIndex) ?: line.episodes.firstOrNull() ?: return ""
        return ep.url
    }

    /**
     * 取完整数据。线路顺序在这里统一定一次，
     * detail() 与 playUrl() 都走缓存，保证两边的 lineIndex 含义一致。
     */
    private suspend fun loadFull(vodId: String): MaccmsVod? {
        fullCache[vodId]?.let { if (it.lines.isNotEmpty()) return it }
        val vod = fetchByIds(listOf(vodId)).firstOrNull { it.id == vodId } ?: return null
        val ordered = vod.withPlayableLinesFirst()
        fullCache[vodId] = ordered
        return ordered
    }

    private fun MaccmsVod.withPlayableLinesFirst(): MaccmsVod {
        if (lines.size <= 1) return this
        val ranked = lines.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<MaccmsLine>> { it.value.playability() }
                    .thenBy { it.index }
            )
            .map { it.value }
        return copy(lines = ranked)
    }

    /**
     * 线路可播性打分。
     * 直链 +4，分享页 -6，线路名带 m3u8 再 +1。
     * 这样 `dyttm3u8` 会排到 `dytt`（分享页）前面。
     */
    private fun MaccmsLine.playability(): Int {
        var score = 0
        episodes.take(5).forEach { e ->
            val u = e.url.lowercase()
            if (u.contains(".m3u8") || u.contains(".mp4")) score += 4
            if (u.contains("/share/")) score -= 6
        }
        if (flag.contains("m3u8", true)) score += 1
        return score
    }

    /* ==================== HTTP ==================== */

    private suspend fun fetch(params: List<Pair<String, Any?>>): MaccmsPage {
        val page = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val url = MaccmsUrl.buildUrl(apiBase, params)
                var last = MaccmsPage()
                for (attempt in 0..1) {
                    val body = runCatching {
                        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            resp.body?.string() ?: error("空响应")
                        }
                    }.getOrNull()

                    if (body != null) {
                        last = MaccmsParser.parse(body)
                        // code=1 说明请求成功，即便结果为空也不再重试
                        if (last.ok) break
                    }
                    if (attempt == 0) delay(250)
                }
                last
            }
        } ?: MaccmsPage()

        return absorb(page)
    }

    private suspend fun fetchByIds(ids: List<String>): List<MaccmsVod> {
        if (ids.isEmpty()) return emptyList()
        val out = mutableListOf<MaccmsVod>()
        ids.chunked(20).forEach { chunk ->
            out += fetch(listOf("ac" to "detail", "pg" to 1, "ids" to chunk.joinToString(","))).items
        }
        return out
    }

    /** 每次拿到数据顺手喂给缓存与索引，浏览过程中索引会自己长大 */
    private fun absorb(page: MaccmsPage): MaccmsPage {
        page.items.forEach { v ->
            if (v.name.isNotBlank()) {
                index[v.id] = IndexEntry(v.id, v.name, HanziInitials.of(v.name), v.en)
            }
            if (v.lines.isNotEmpty()) fullCache[v.id] = v.withPlayableLinesFirst()
        }
        if (page.classes.isNotEmpty()) classCache = page.classes
        return page
    }

    private fun MaccmsVod.toSummary() = VodSummary(
        id = id,
        title = name,
        cover = pic,
        score = score,
        year = year,
        region = area,
        category = typeName,
        remarks = remarks,
        initials = HanziInitials.of(name)
    )

    private companion object {
        /** detail 单页 20 条约 500KB，给足时间 */
        const val REQUEST_TIMEOUT_MS = 12_000L
        const val CLASS_TIMEOUT_MS = 8_000L

        /** 首字母索引预热页数：6 页 × 6KB = 36KB，换来 120 条兜底数据 */
        const val INDEX_PAGES = 6
        const val INDEX_MIN = 120
        const val MAX_SEARCH_HITS = 24
    }
}

/**
 * 苹果 CMS 接口的结构化探活。
 *
 * 相比「HTTP 200 就算通」，这里要求：返回得动、`code=1`、列表非空、
 * 且最终落地域名和配置域名一致（防止域名被抢注后 302 到别的站）。
 */
object MaccmsProbe {

    suspend fun check(
        client: OkHttpClient,
        rawUrl: String,
        timeoutMs: Long = 6_000L
    ): ProbeResult {
        val base = MaccmsUrl.normalize(rawUrl)
            ?: return ProbeResult.Failed("地址格式不对")
        val expectedHost = hostOf(base) ?: return ProbeResult.Failed("地址格式不对")
        val started = System.currentTimeMillis()

        val outcome = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val url = MaccmsUrl.buildUrl(base, listOf("ac" to "list", "pg" to 1))
                    client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                        // resp.request.url 是跟随跳转后的最终地址
                        Triple(resp.code, resp.request.url.host, MaccmsParser.parse(resp.body?.string().orEmpty()))
                    }
                }
            }
        }

        if (outcome == null) return ProbeResult.Failed("超时（${timeoutMs}ms）")
        val failure = outcome.exceptionOrNull()
        if (failure != null) return ProbeResult.Failed(failure.friendly())

        val (code, finalHost, page) = outcome.getOrThrow()
        if (code !in 200..399) return ProbeResult.Failed("HTTP $code")
        if (!sameHost(expectedHost, finalHost)) {
            return ProbeResult.Failed("跳到 $finalHost，疑似劫持")
        }
        if (!page.ok) return ProbeResult.Failed("返回不是苹果 CMS 接口")
        if (page.items.isEmpty()) return ProbeResult.Failed("接口正常但列表为空")

        val cost = System.currentTimeMillis() - started
        return ProbeResult.Ok(cost)
    }

    private fun hostOf(url: String): String? =
        url.substringAfter("://", "").substringBefore('/').substringBefore('?').ifBlank { null }

    /** 允许 www 前缀差异，其余一律视为异常跳转 */
    private fun sameHost(a: String, b: String): Boolean =
        a.equals(b, true) || a.removePrefix("www.").equals(b.removePrefix("www."), true)

    private fun Throwable.friendly(): String = when (this) {
        is java.net.UnknownHostException -> "域名解析失败"
        is java.net.SocketTimeoutException -> "连接超时"
        is javax.net.ssl.SSLException -> "TLS 握手失败"
        is java.io.IOException -> "网络不可达"
        else -> message ?: this::class.java.simpleName
    }
}

/** 界面上的地区名与 maccms `area` 字段的取值不是一回事，这里做一次映射 */
object MaccmsRegion {

    private val MAP = mapOf(
        "大陆" to "大陆",
        "中国香港" to "香港",
        "香港" to "香港",
        "中国台湾" to "台湾",
        "台湾" to "台湾",
        "日本" to "日本",
        "韩国" to "韩国",
        "美国" to "美国"
        // 「欧美」在 area 字段里没有对应值，交给分类（欧美剧）去筛
    )

    fun map(region: String?): String? {
        val r = region?.trim().orEmpty()
        if (r.isEmpty() || r == "全部") return null
        return MAP[r]
    }
}
