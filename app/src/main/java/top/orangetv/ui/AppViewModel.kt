package top.orangetv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.orangetv.OrangeTvApp
import top.orangetv.config.EndpointStatus
import top.orangetv.data.model.SortOrder
import top.orangetv.data.model.VodCategory
import top.orangetv.data.model.VodDetail
import top.orangetv.data.model.VodRow
import top.orangetv.data.model.VodSummary
import top.orangetv.data.settings.PlaybackStore
import top.orangetv.data.settings.SourceStore
import top.orangetv.data.source.SourceRegistry
import top.orangetv.data.source.VodSource

/**
 * 全局单例 ViewModel：持有源状态、首页数据、主题、待播放请求。
 * 页面级别的临时状态（输入内容、选中频道）各自 remember。
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as OrangeTvApp).container

    val registry: SourceRegistry = container.registry
    val vodState: StateFlow<SourceRegistry.VodState> = registry.vodState
    val liveState: StateFlow<SourceRegistry.LiveState> = registry.liveState

    private val _themeId = MutableStateFlow(container.store.themeId)
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    private val _rows = MutableStateFlow<List<VodRow>>(emptyList())
    val rows: StateFlow<List<VodRow>> = _rows.asStateFlow()

    private val _homeLoading = MutableStateFlow(true)
    val homeLoading: StateFlow<Boolean> = _homeLoading.asStateFlow()

    data class Playback(
        val url: String,
        val title: String,
        val subtitle: String,
        val vodId: String = "",
        val line: Int = 0,
        val episode: Int = 0
    ) {
        /**
         * 进度记忆键：影片 + 线路 + 集数。
         * 不含播放地址，源换域名后进度照样接得上。
         */
        val key: String
            get() = if (vodId.isBlank()) "" else "$vodId#$line#$episode"

        val rememberProgress: Boolean get() = key.isNotBlank()
    }

    private val _playback = MutableStateFlow<Playback?>(null)
    val playback: StateFlow<Playback?> = _playback.asStateFlow()

    /** 所有片子的断点，倒序；详情页据此显示「继续观看」 */
    private val _resumes = MutableStateFlow(container.playbackStore.all())
    val resumes: StateFlow<List<PlaybackStore.Resume>> = _resumes.asStateFlow()

    /**
     * 扫码配置页状态。
     * token / 端口都在 pageUrl 里，UI 只需要把它画成二维码。
     */
    data class ConfigUi(
        val running: Boolean = false,
        val pageUrl: String? = null,
        val error: String? = null,
        val vod: List<EndpointStatus> = emptyList(),
        val live: List<EndpointStatus> = emptyList(),
        val checkedAt: Long = 0L
    )

    private val _configUi = MutableStateFlow(ConfigUi())
    val configUi: StateFlow<ConfigUi> = _configUi.asStateFlow()

    init {
        viewModelScope.launch {
            vodState.collect { state ->
                if (state is SourceRegistry.VodState.Ready) {
                    _homeLoading.value = true
                    _rows.value = runCatching { state.source.rows() }.getOrElse { emptyList() }
                    _homeLoading.value = false
                } else if (state is SourceRegistry.VodState.Unavailable) {
                    _homeLoading.value = false
                }
            }
        }
        // 探活是异步的，跑完一轮就刷新一次列表里的状态点
        viewModelScope.launch {
            container.revision.collect { refreshConfigSnapshot() }
        }
    }

    fun setTheme(id: String) {
        _themeId.value = id
        container.store.themeId = id
    }

    fun requestPlayback(
        url: String,
        title: String,
        subtitle: String,
        vodId: String = "",
        line: Int = 0,
        episode: Int = 0
    ) {
        _playback.value = Playback(url, title, subtitle, vodId, line, episode)
    }

    fun clearPlayback() {
        _playback.value = null
    }

    /* ---------------- 进度记忆 ---------------- */

    fun resumeOf(key: String): PlaybackStore.Resume? = container.playbackStore.get(key)

    fun saveProgress(play: Playback, positionMs: Long, durationMs: Long) {
        if (!play.rememberProgress || positionMs <= 0L) return
        container.playbackStore.save(
            PlaybackStore.Resume(
                key = play.key,
                vodId = play.vodId,
                line = play.line,
                episode = play.episode,
                positionMs = positionMs,
                durationMs = durationMs,
                title = play.title,
                updatedAt = System.currentTimeMillis()
            )
        )
        _resumes.value = container.playbackStore.all()
    }

    fun forgetProgress(key: String) {
        if (key.isBlank()) return
        container.playbackStore.remove(key)
        _resumes.value = container.playbackStore.all()
    }

    /** 全局默认倍速，播放页改动后写回，下次进播放器沿用 */
    var speed: Float
        get() = container.playbackStore.speed
        set(value) {
            container.playbackStore.speed = value
        }

    fun refreshSources() = container.rebuild()

    /* ---------------- 扫码配置 ---------------- */

    /** 进配置页时调用：没启动就把服务拉起来，然后刷一次快照 */
    fun openConfigPanel() {
        if (!_configUi.value.running) startConfigServer()
        refreshConfigSnapshot()
    }

    fun startConfigServer() {
        val info = runCatching { container.startConfigServer() }.getOrNull()
        _configUi.value = if (info == null) {
            _configUi.value.copy(
                running = false,
                pageUrl = null,
                error = container.configServer.lastError ?: "配置服务启动失败"
            )
        } else {
            _configUi.value.copy(running = true, pageUrl = info.pageUrl, error = null)
        }
    }

    fun stopConfigServer() {
        container.stopConfigServer()
        _configUi.value = _configUi.value.copy(running = false, pageUrl = null)
    }

    fun reprobeSources() {
        container.reprobe()
        refreshConfigSnapshot()
    }

    /**
     * 电视端手动添加一条源（遥控器调出输入法输入）。
     * 地址重复时忽略；成功后重建源并探活，状态点会自动刷新。
     */
    fun addEndpoint(isLive: Boolean, name: String, url: String) {
        val u = url.trim()
        if (u.isEmpty()) return
        val n = name.trim().ifEmpty { if (isLive) "直播源" else "点播源" }
        val ep = SourceStore.Endpoint(n, u)
        if (isLive) {
            val cur = container.store.liveEndpoints
            if (cur.any { it.url == u }) return
            container.store.liveEndpoints = cur + ep
        } else {
            val cur = container.store.vodEndpoints
            if (cur.any { it.url == u }) return
            container.store.vodEndpoints = cur + ep
        }
        container.rebuild()
        container.reprobe()
        refreshConfigSnapshot()
    }

    private fun refreshConfigSnapshot() {
        val snapshot = container.snapshot()
        _configUi.value = _configUi.value.copy(
            vod = snapshot.vod,
            live = snapshot.live,
            checkedAt = snapshot.checkedAt
        )
    }

    private fun currentSource(): VodSource? =
        (vodState.value as? SourceRegistry.VodState.Ready)?.source

    suspend fun browse(
        category: String?,
        region: String?,
        year: String?,
        sort: SortOrder
    ): List<VodSummary> = runCatching {
        currentSource()?.browse(category, region, year, sort) ?: emptyList()
    }.getOrElse { emptyList() }

    suspend fun search(keyword: String): List<VodSummary> = runCatching {
        currentSource()?.search(keyword) ?: emptyList()
    }.getOrElse { emptyList() }

    /** 源自己提供的分类表；空表示该源没有，UI 退回内置粗分类 */
    suspend fun categories(): List<VodCategory> = runCatching {
        currentSource()?.categories() ?: emptyList()
    }.getOrElse { emptyList() }

    suspend fun detail(id: String): VodDetail? = runCatching {
        currentSource()?.detail(id)
    }.getOrNull()

    suspend fun playUrl(vodId: String, line: Int, episode: Int): String? = runCatching {
        currentSource()?.playUrl(vodId, line, episode)
    }.getOrNull()
}
