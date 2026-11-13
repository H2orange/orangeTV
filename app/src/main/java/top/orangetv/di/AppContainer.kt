package top.orangetv.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import top.orangetv.config.ConfigBridge
import top.orangetv.config.ConfigPage
import top.orangetv.config.ConfigSnapshot
import top.orangetv.config.EndpointInput
import top.orangetv.config.EndpointStatus
import top.orangetv.config.LocalConfigServer
import top.orangetv.data.settings.PlaybackStore
import top.orangetv.data.settings.SourceStore
import top.orangetv.data.source.AssetLiveSource
import top.orangetv.data.source.DemoVodSource
import top.orangetv.data.source.HealthProbe
import top.orangetv.data.source.LiveSource
import top.orangetv.data.source.M3uLiveSource
import top.orangetv.data.source.ProbeResult
import top.orangetv.data.source.SourceRegistry
import top.orangetv.data.source.VodSource
import top.orangetv.data.source.maccms.MaccmsProbe
import top.orangetv.data.source.maccms.MaccmsVodSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 手写依赖容器，够用就好，不引 Hilt。
 *
 * 同时承担 ConfigBridge 的实现：手机配置页读写的都是这里的东西，
 * 所以由容器自己来做转换，服务器和 UI 两边都不用知道对方存在。
 */
class AppContainer(context: Context) : ConfigBridge {

    val store = SourceStore(context.applicationContext)

    /** 播放进度 + 倍速记忆 */
    val playbackStore = PlaybackStore(context.applicationContext)

    private val appContext = context.applicationContext

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val probe = HealthProbe(client)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val registry = SourceRegistry(scope)

    /** 每个配置地址最近一次的探活结果，key = "vod|url" / "live|url" */
    private val statusMap = ConcurrentHashMap<String, ProbeResult>()

    /** 探活批次计数：UI 收到变化就去重读快照 */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    @Volatile
    private var lastCheckedAt: Long = 0L

    /** 手机扫码配置服务，改配置后由 AppViewModel 驱动 */
    val configServer: LocalConfigServer by lazy {
        val page = ConfigPage(appContext)
        LocalConfigServer(bridge = this, pageProvider = { page.render() })
    }

    init {
        rebuild()
    }

    /**
     * 按当前配置重新装配源并探活。
     * 配置变更（手机扫码写入）后调用。
     */
    fun rebuild() {
        val vodFactories = buildList<() -> VodSource> {
            // 用户配置的点播源：目前只有一种协议适配器（苹果 CMS / maccms），
            // 电影天堂、厂长这类资源站都是同一套接口。地址由用户在配置页填写。
            store.vodEndpoints
                .filter { it.enabled && it.url.isNotBlank() }
                .forEachIndexed { index, endpoint ->
                    add {
                        MaccmsVodSource(
                            id = "vod_${endpoint.url.hashCode()}_$index",
                            displayName = endpoint.name.ifBlank { "点播源 ${index + 1}" },
                            priority = index,
                            rawUrl = endpoint.url,
                            client = client
                        )
                    }
                }
            // 一条都没配时用内置演示源，保证装上就能看到界面
            if (isEmpty()) add { DemoVodSource() }
        }

        val liveFactories = buildList<() -> LiveSource> {
            add {
                AssetLiveSource(
                    context = appContext,
                    client = client
                )
            }
            // 用户配置的 M3U，按填写顺序决定优先级
            store.liveEndpoints
                .filter { it.enabled && it.url.isNotBlank() }
                .forEachIndexed { index, endpoint ->
                    add {
                        M3uLiveSource(
                            id = "live_${endpoint.url.hashCode()}_$index",
                            displayName = endpoint.name.ifBlank { "直播源 ${index + 1}" },
                            priority = index,
                            url = endpoint.url,
                            client = client,
                            probe = probe
                        )
                    }
                }
        }

        registry.configure(vod = vodFactories, live = liveFactories)
        probeConfigured()
    }

    /** 独立于注册表再探一遍：注册表只关心"能不能用"，这里要的是"每一条各自的状态" */
    private fun probeConfigured() {
        val vod = store.vodEndpoints
        val live = store.liveEndpoints
        if (vod.isEmpty() && live.isEmpty()) {
            lastCheckedAt = System.currentTimeMillis()
            _revision.value++
            return
        }

        scope.launch {
            coroutineScope {
                val jobs = buildList {
                    // 点播源走结构校验：要求返回得动、code=1、列表非空、没被跳到别的域名。
                    // 只判 HTTP 200 会把劫持页和空接口都当成"可用"。
                    vod.forEach { e -> add(async { "vod|${e.url}" to MaccmsProbe.check(client, e.url) }) }
                    live.forEach { e -> add(async { "live|${e.url}" to probe.check(e.url) }) }
                }
                jobs.awaitAll().forEach { (key, result) -> statusMap[key] = result }
            }
            lastCheckedAt = System.currentTimeMillis()
            _revision.value++
        }
    }

    /* ==================== ConfigBridge ==================== */

    override fun snapshot(): ConfigSnapshot = ConfigSnapshot(
        vod = store.vodEndpoints.map { view("vod", it) },
        live = store.liveEndpoints.map { view("live", it) },
        checkedAt = lastCheckedAt
    )

    override fun save(vod: List<EndpointInput>, live: List<EndpointInput>): ConfigSnapshot {
        store.vodEndpoints = vod.map { SourceStore.Endpoint(it.name, it.url) }
        store.liveEndpoints = live.map { SourceStore.Endpoint(it.name, it.url) }
        statusMap.clear()
        lastCheckedAt = 0L
        rebuild()
        return snapshot()
    }

    override fun reprobe(): ConfigSnapshot {
        statusMap.clear()
        lastCheckedAt = 0L
        rebuild()
        return snapshot()
    }

    private fun view(kind: String, endpoint: SourceStore.Endpoint): EndpointStatus {
        val label = endpoint.name.ifBlank {
            if (kind == "vod") "点播源" else "直播源"
        }
        return when (val result = statusMap["$kind|${endpoint.url}"]) {
            is ProbeResult.Ok -> EndpointStatus(
                name = label,
                url = endpoint.url,
                state = EndpointStatus.State.OK,
                detail = "${result.latencyMs}ms"
            )

            is ProbeResult.Failed -> EndpointStatus(
                name = label,
                url = endpoint.url,
                state = EndpointStatus.State.FAIL,
                detail = result.reason
            )

            null -> EndpointStatus(
                name = label,
                url = endpoint.url,
                state = EndpointStatus.State.UNKNOWN,
                detail = "未探测"
            )
        }
    }

    /* ==================== 配置服务 ==================== */

    fun startConfigServer(): LocalConfigServer.Info? = configServer.start()

    fun stopConfigServer() {
        configServer.stop()
    }
}
