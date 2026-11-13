package top.orangetv.config

/**
 * 手机配置页与 App 之间的桥。
 *
 * 刻意把「协议」和「实现」分开：LocalConfigServer 只负责 HTTP 与页面，
 * 读配置、写配置、触发探活都由这个接口的实现方（AppContainer）来做。
 * 这样服务器不知道源是什么，源也不需要知道有服务器。
 */

/** 手机提交上来的一条配置 */
data class EndpointInput(val name: String, val url: String)

/** 展示给手机 / 电视的一条配置状态 */
data class EndpointStatus(
    val name: String,
    val url: String,
    val state: State,
    val detail: String
) {
    enum class State { OK, FAIL, UNKNOWN }
}

data class ConfigSnapshot(
    val vod: List<EndpointStatus> = emptyList(),
    val live: List<EndpointStatus> = emptyList(),
    val checkedAt: Long = 0L
)

interface ConfigBridge {

    /** 当前配置 + 最近一次探活结果（同步读内存，不发网络请求） */
    fun snapshot(): ConfigSnapshot

    /** 手机点「保存并同步」后调用：落盘 → 重新装配源 → 探活 */
    fun save(vod: List<EndpointInput>, live: List<EndpointInput>): ConfigSnapshot

    /** 手机点「重新探活」 */
    fun reprobe(): ConfigSnapshot
}
