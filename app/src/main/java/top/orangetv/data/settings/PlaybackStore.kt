package top.orangetv.data.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 播放进度与倍速的持久化。
 *
 * 进度以「影片 id + 线路 + 集数」为键，和播放地址解耦——
 * 源换了 CDN、地址变了，只要还是同一集，进度就能接上。
 *
 * 只保留最近 [MAX_ENTRIES] 条，按更新时间倒序，避免无限增长。
 */
class PlaybackStore(context: Context) {

    private val sp = context.getSharedPreferences("orange_tv_playback", Context.MODE_PRIVATE)

    data class Resume(
        val key: String,
        val vodId: String,
        val line: Int,
        val episode: Int,
        val positionMs: Long,
        val durationMs: Long,
        val title: String,
        val updatedAt: Long
    ) {
        val fraction: Float
            get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    /** 全局默认倍速，播放页改动后写回这里 */
    var speed: Float
        get() {
            val v = sp.getFloat(KEY_SPEED, 1f)
            return if (v in MIN_SPEED..MAX_SPEED) v else 1f
        }
        set(value) {
            sp.edit().putFloat(KEY_SPEED, value.coerceIn(MIN_SPEED, MAX_SPEED)).apply()
        }

    fun get(key: String): Resume? {
        if (key.isBlank()) return null
        return all().firstOrNull { it.key == key }
    }

    /** 按更新时间倒序 */
    fun all(): List<Resume> {
        val raw = sp.getString(KEY_RESUME, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val key = o.optString("key")
                if (key.isBlank()) return@mapNotNull null
                Resume(
                    key = key,
                    vodId = o.optString("vodId"),
                    line = o.optInt("line"),
                    episode = o.optInt("episode"),
                    positionMs = o.optLong("positionMs"),
                    durationMs = o.optLong("durationMs"),
                    title = o.optString("title"),
                    updatedAt = o.optLong("updatedAt")
                )
            }.sortedByDescending { it.updatedAt }
        }.getOrElse { emptyList() }
    }

    /** 同一部片最近看的那一集，用于详情页「继续观看」 */
    fun latestForVod(vodId: String): Resume? {
        if (vodId.isBlank()) return null
        return all().firstOrNull { it.vodId == vodId }
    }

    fun save(resume: Resume) {
        val kept = all().filterNot { it.key == resume.key }
        val next = (listOf(resume) + kept)
            .sortedByDescending { it.updatedAt }
            .take(MAX_ENTRIES)
        write(next)
    }

    fun remove(key: String) {
        val current = all()
        val next = current.filterNot { it.key == key }
        if (next.size == current.size) return
        write(next)
    }

    private fun write(list: List<Resume>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("key", r.key)
                    .put("vodId", r.vodId)
                    .put("line", r.line)
                    .put("episode", r.episode)
                    .put("positionMs", r.positionMs)
                    .put("durationMs", r.durationMs)
                    .put("title", r.title)
                    .put("updatedAt", r.updatedAt)
            )
        }
        sp.edit().putString(KEY_RESUME, arr.toString()).apply()
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_RESUME = "resume_list"
        const val MAX_ENTRIES = 60
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}
