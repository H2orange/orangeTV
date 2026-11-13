package top.orangetv.data.source

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import top.orangetv.data.m3u.M3uParser
import top.orangetv.data.model.Channel

/**
 * 内置的演示直播源（读取 assets/demo_live.m3u）。
 * 里面是公开测试流，保证首次安装就能看到播放效果。
 */
class AssetLiveSource(
    private val context: Context,
    override val id: String = "asset_demo",
    override val displayName: String = "内置演示频道",
    override val priority: Int = 998,
    private val client: OkHttpClient
) : LiveSource {

    @Volatile
    private var cached: List<Channel>? = null

    override suspend fun probe(): ProbeResult = ProbeResult.Ok(0)

    override suspend fun channels(): List<Channel> {
        cached?.let { return it }
        val text = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("demo_live.m3u").bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return emptyList()

        val list = M3uParser.parse(text).mapIndexed { i, e ->
            Channel(
                id = "$id#$i",
                name = e.name,
                group = e.group,
                logo = e.logo,
                url = e.url,
                sourceId = id
            )
        }
        cached = list
        return list
    }
}
