package top.orangetv.data.source

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.orangetv.data.model.Channel

/**
 * 源注册表 + 启动探活调度。
 *
 * 启动流程：
 * 1. 按 priority 升序实例化候选源
 * 2. 并发探活（每个 2 秒超时，互不阻塞）
 * 3. 点播取第一个可用的作为当前源；直播合并所有可用的频道
 * 4. 全部失败 -> 明确的不可用状态，交给 UI 提示用户去改配置
 *
 * 这里刻意不做的事情：不联网搜索"最新地址"、不猜测镜像站。
 * 地址永远来自用户配置，避免被仿冒站顶替。
 */
class SourceRegistry(
    private val scope: CoroutineScope
) {

    data class SourceStatus(
        val source: RemoteSource,
        val result: ProbeResult
    )

    sealed interface VodState {
        data object Probing : VodState
        data class Ready(
            val source: VodSource,
            val latencyMs: Long,
            val all: List<SourceStatus>
        ) : VodState
        data class Unavailable(val all: List<SourceStatus>) : VodState
    }

    sealed interface LiveState {
        data object Probing : LiveState
        data class Ready(
            val channels: List<Channel>,
            val usableSources: List<LiveSource>,
            val all: List<SourceStatus>
        ) : LiveState
        data class Unavailable(val all: List<SourceStatus>) : LiveState
    }

    private val _vodState = MutableStateFlow<VodState>(VodState.Probing)
    val vodState: StateFlow<VodState> = _vodState.asStateFlow()

    private val _liveState = MutableStateFlow<LiveState>(LiveState.Probing)
    val liveState: StateFlow<LiveState> = _liveState.asStateFlow()

    private var vodFactories: List<() -> VodSource> = emptyList()
    private var liveFactories: List<() -> LiveSource> = emptyList()

    /** 配置变更后重新装配候选源，并触发一次探活 */
    fun configure(
        vod: List<() -> VodSource>,
        live: List<() -> LiveSource>
    ) {
        vodFactories = vod
        liveFactories = live
        refresh()
    }

    fun refresh() = scope.launch {
        probeVod()
        probeLive()
    }

    private suspend fun probeVod() = coroutineScope {
        _vodState.value = VodState.Probing
        val instances = vodFactories.map { it() }.sortedBy { it.priority }
        if (instances.isEmpty()) {
            _vodState.value = VodState.Unavailable(emptyList())
            return@coroutineScope
        }

        val results = instances
            .map { source -> async { SourceStatus(source, safelyProbe(source)) } }
            .awaitAll()

        val winner = results.firstOrNull { it.result is ProbeResult.Ok }
        _vodState.value = if (winner != null) {
            val latency = (winner.result as ProbeResult.Ok).latencyMs
            VodState.Ready(winner.source as VodSource, latency, results)
        } else {
            VodState.Unavailable(results)
        }
    }

    private suspend fun probeLive() = coroutineScope {
        _liveState.value = LiveState.Probing
        val instances = liveFactories.map { it() }.sortedBy { it.priority }
        if (instances.isEmpty()) {
            _liveState.value = LiveState.Unavailable(emptyList())
            return@coroutineScope
        }

        val results = instances
            .map { source -> async { SourceStatus(source, safelyProbe(source)) } }
            .awaitAll()

        val usable = results
            .filter { it.result is ProbeResult.Ok }
            .map { it.source as LiveSource }

        if (usable.isEmpty()) {
            _liveState.value = LiveState.Unavailable(results)
            return@coroutineScope
        }

        // 多个可用源的频道合并，同源频道按 id 去重
        val channels = usable
            .flatMap { source ->
                runCatching { source.channels() }.getOrElse { emptyList() }
            }
            .distinctBy { it.id }

        _liveState.value = LiveState.Ready(channels, usable, results)
    }

    private suspend fun safelyProbe(source: RemoteSource): ProbeResult =
        runCatching { source.probe() }
            .getOrElse { ProbeResult.Failed(it.message ?: "未知错误") }
}
