package top.orangetv.data.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 源配置持久化。
 *
 * 配置只保存在本机，支持多候选地址（按顺序探活）。
 * 配置的写入入口是手机扫码页 / 手动输入，App 自身不会去发现新地址。
 */
class SourceStore(context: Context) {

    private val sp = context.getSharedPreferences("orange_tv_sources", Context.MODE_PRIVATE)

    data class Endpoint(
        val name: String,
        val url: String,
        val enabled: Boolean = true
    )

    var vodEndpoints: List<Endpoint>
        get() = read(KEY_VOD)
        set(value) = write(KEY_VOD, value)

    var liveEndpoints: List<Endpoint>
        get() = read(KEY_LIVE)
        set(value) = write(KEY_LIVE, value)

    var themeId: String
        get() = sp.getString(KEY_THEME, "coral") ?: "coral"
        set(value) = sp.edit().putString(KEY_THEME, value).apply()

    private fun read(key: String): List<Endpoint> {
        val raw = sp.getString(key, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Endpoint(
                    name = o.optString("name"),
                    url = o.optString("url"),
                    enabled = o.optBoolean("enabled", true)
                )
            }
        }.getOrElse { emptyList() }
    }

    private fun write(key: String, list: List<Endpoint>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("name", e.name)
                    .put("url", e.url)
                    .put("enabled", e.enabled)
            )
        }
        sp.edit().putString(key, arr.toString()).apply()
    }

    private companion object {
        const val KEY_VOD = "vod_endpoints"
        const val KEY_LIVE = "live_endpoints"
        const val KEY_THEME = "theme_id"
    }
}
