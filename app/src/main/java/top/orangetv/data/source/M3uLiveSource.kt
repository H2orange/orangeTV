package top.orangetv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import top.orangetv.data.m3u.M3uParser
import top.orangetv.data.model.Channel

/**
 * 基于用户配置的 M3U / TXT 地址的直播源。
 *
 * 地址完全由用户提供（扫码或手动填写），App 只负责下载和解析。
 */
class M3uLiveSource(
    override val id: String,
    override val displayName: String,
    override val priority: Int,
    private val url: String,
    private val client: OkHttpClient,
    private val probe: HealthProbe
) : LiveSource {

    @Volatile
    private var cached: List<Channel>? = null

    override suspend fun probe(): ProbeResult = probe.check(url)

    override suspend fun channels(): List<Channel> {
        cached?.let { return it }
        val list = download() ?: return emptyList()
        val entries = M3uParser.parse(list)
        val result = entries.mapIndexed { i, e ->
            Channel(
                id = "$id#${e.url.hashCode()}#$i",
                name = e.name,
                group = e.group,
                logo = e.logo,
                url = e.url,
                sourceId = id
            )
        }
        cached = result
        return result
    }

    private suspend fun download(): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }
}
