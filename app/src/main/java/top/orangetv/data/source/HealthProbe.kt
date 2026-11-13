package top.orangetv.data.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 统一探活实现。
 *
 * 判定标准刻意保持简单保守：
 * - 2 秒超时（电视端用户等不了更久）
 * - 只要 HTTP 状态码在 200..399 就算通
 * - 不解析业务字段，避免把"源改名"误判成"源挂了"
 */
class HealthProbe(
    private val client: OkHttpClient,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    suspend fun check(url: String, headers: Map<String, String> = emptyMap()): ProbeResult {
        val started = System.currentTimeMillis()
        val outcome = withTimeoutOrNull(timeoutMs) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val builder = Request.Builder().url(url).get()
                    headers.forEach { (k, v) -> builder.header(k, v) }
                    client.newCall(builder.build()).execute().use { resp ->
                        resp.code
                    }
                }
            }
        }

        return when {
            outcome == null -> ProbeResult.Failed("超时（${timeoutMs}ms）")
            outcome.isFailure -> ProbeResult.Failed(
                outcome.exceptionOrNull()?.friendlyMessage() ?: "请求失败"
            )
            else -> {
                val code = outcome.getOrThrow()
                val cost = System.currentTimeMillis() - started
                if (code in 200..399) ProbeResult.Ok(cost) else ProbeResult.Failed("HTTP $code")
            }
        }
    }

    private fun Throwable.friendlyMessage(): String = when (this) {
        is java.net.UnknownHostException -> "域名解析失败"
        is java.net.SocketTimeoutException -> "连接超时"
        is javax.net.ssl.SSLException -> "TLS 握手失败"
        is IOException -> "网络不可达"
        else -> message ?: this::class.java.simpleName
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2_000L
    }
}
