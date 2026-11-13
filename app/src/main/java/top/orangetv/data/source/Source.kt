package top.orangetv.data.source

import top.orangetv.data.model.Channel
import top.orangetv.data.model.SortOrder
import top.orangetv.data.model.VodCategory
import top.orangetv.data.model.VodDetail
import top.orangetv.data.model.VodRow
import top.orangetv.data.model.VodSummary

/**
 * 探活结果。所有网络源在启动时都要跑一次，只有 Ok 的才会被选用。
 */
sealed interface ProbeResult {
    data class Ok(val latencyMs: Long) : ProbeResult
    data class Failed(val reason: String) : ProbeResult
}

/**
 * 远程源的基础契约。
 *
 * 关键约定：**地址来自用户配置，App 不负责发现地址**。
 * 探活只做"这个地址现在能不能用"的判断，不会去猜或搜索新地址。
 */
interface RemoteSource {
    /** 稳定标识，用于配置持久化 */
    val id: String

    /** 展示名，出现在设置页 */
    val displayName: String

    /** 越小越优先 */
    val priority: Int

    /** 探活：2 秒内必须返回，超时视为 Failed */
    suspend fun probe(): ProbeResult
}

/**
 * 点播源。
 *
 * 实现方负责把站点数据映射成这里的模型；
 * UI 只依赖这个接口，换源不需要改任何界面代码。
 */
interface VodSource : RemoteSource {
    /** 首页内容行（继续观看由本地历史单独拼，不在这里） */
    suspend fun rows(): List<VodRow>

    /**
     * 可筛选分类。
     *
     * 返回空表示「这个源不提供分类表」，UI 会退回内置的粗分类。
     * 苹果 CMS 源的分类表来自 `<class>` 字典，是真实的二级分类。
     */
    suspend fun categories(): List<VodCategory> = emptyList()

    /** 分类筛选 / 浏览 */
    suspend fun browse(
        category: String? = null,
        region: String? = null,
        year: String? = null,
        sort: SortOrder = SortOrder.Latest,
        page: Int = 1
    ): List<VodSummary>

    /** 搜索。keyword 可能是中文，也可能是首字母 */
    suspend fun search(keyword: String, page: Int = 1): List<VodSummary>

    /** 详情 */
    suspend fun detail(id: String): VodDetail

    /** 取播放地址 */
    suspend fun playUrl(vodId: String, lineIndex: Int, episodeIndex: Int): String
}

/**
 * 直播源。一个源 = 一份 M3U / TXT 播放列表。
 */
interface LiveSource : RemoteSource {
    suspend fun channels(): List<Channel>
}
