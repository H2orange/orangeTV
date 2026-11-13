package top.orangetv.config

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import top.orangetv.util.LanAddress
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * 电视端局域网配置服务。
 *
 * 为什么要有它：在电视上用遥控器敲一个 M3U 地址属于酷刑，
 * 所以起一个极小的 HTTP 服务，手机扫码直接在浏览器里填，提交后立刻生效。
 *
 * 边界：
 * - 只绑局域网地址，不做公网穿透，也没有任何外网出口
 * - 每个请求都要带会话 token（每次启动重新生成），同网段的人扫不到码就改不了
 * - 手写的 HTTP/1.1，只实现 GET / POST，不支持 keep-alive，够用且没有依赖
 */
class LocalConfigServer(
    private val bridge: ConfigBridge,
    private val pageProvider: () -> String,
    private val portCandidates: IntRange = 8080..8090
) {

    data class Info(val ip: String, val port: Int, val token: String) {
        /** 二维码内容 = 手机打开的表单页 */
        val pageUrl: String get() = "http://$ip:$port/?t=$token"
    }

    @Volatile
    var info: Info? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var running = false

    private var serverSocket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)
    private val random = SecureRandom()

    @Synchronized
    fun start(): Info? {
        info?.let { if (running) return it }
        lastError = null

        val ip = LanAddress.pick()
        if (ip == null) {
            lastError = "没找到局域网地址，请确认电视已连上 Wi-Fi 或网线"
            return null
        }

        val socket = bind() ?: return null

        serverSocket = socket
        val token = newToken()
        info = Info(ip, socket.localPort, token)
        running = true

        thread(isDaemon = true, name = "orange-config-accept") { acceptLoop(socket) }
        Log.i(TAG, "配置服务已启动: ${info?.pageUrl}")
        return info
    }

    @Synchronized
    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        info = null
    }

    private fun bind(): ServerSocket? {
        for (port in portCandidates) {
            try {
                val socket = ServerSocket()
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                return socket
            } catch (e: Exception) {
                // 端口被占，换下一个
            }
        }
        lastError = "端口 ${portCandidates.first}..${portCandidates.last} 都被占用了"
        return null
    }

    private fun newToken(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        // 注意按无符号处理，否则负数会 format 成 16 位
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun acceptLoop(server: ServerSocket) {
        while (running && !server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: Exception) {
                break
            }
            runCatching { pool.execute { handle(client) } }
                .onFailure { runCatching { client.close() } }
        }
    }

    /* ==================== 请求处理 ==================== */

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 8000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val requestLine = readLine(input)
            if (requestLine.isNullOrBlank()) return

            val parts = requestLine.split(' ')
            if (parts.size < 2) {
                write(output, 400, TEXT, "Bad Request")
                return
            }

            val method = parts[0].uppercase()
            val target = parts[1]
            val path = target.substringBefore('?')
            val query = parseQuery(target.substringAfter('?', ""))

            // 读完 header，至少要把 body 读掉，否则 close 时可能触发 RST
            var contentLength = 0
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val idx = line.indexOf(':')
                if (idx <= 0) continue
                if (line.substring(0, idx).trim().equals("content-length", true)) {
                    contentLength = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                }
            }

            if (contentLength > MAX_BODY) {
                write(output, 413, TEXT, "内容过大")
                return
            }
            val body = if (contentLength > 0) readBody(input, contentLength) else ""

            // 鉴权：token 不匹配一律拒绝，避免同网段别人翻到端口就能改配置
            if (!tokenMatches(query["t"])) {
                write(output, 403, HTML, deniedPage())
                return
            }

            when {
                method == "GET" && (path == "/" || path == "/index.html") ->
                    write(output, 200, HTML, pageProvider())

                method == "GET" && path == "/api/state" ->
                    write(output, 200, JSON, snapshotJson(bridge.snapshot()))

                method == "POST" && path == "/api/save" -> {
                    val parsed = runCatching { parseSaveBody(body) }.getOrNull()
                    if (parsed == null) {
                        write(output, 400, JSON, errorJson("配置格式不对"))
                    } else {
                        val snapshot = bridge.save(parsed.first, parsed.second)
                        write(output, 200, JSON, snapshotJson(snapshot))
                    }
                }

                method == "POST" && path == "/api/probe" ->
                    write(output, 200, JSON, snapshotJson(bridge.reprobe()))

                else -> write(output, 404, TEXT, "Not Found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "处理请求失败: ${e.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun tokenMatches(token: String?): Boolean {
        val expected = info?.token ?: return false
        if (token == null) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            token.toByteArray(Charsets.UTF_8)
        )
    }

    /* ==================== 序列化 ==================== */

    private fun snapshotJson(snapshot: ConfigSnapshot): String {
        val root = JSONObject()
        root.put("ok", true)
        root.put("checkedAt", snapshot.checkedAt)
        root.put("vod", statusArray(snapshot.vod))
        root.put("live", statusArray(snapshot.live))
        return root.toString()
    }

    private fun statusArray(list: List<EndpointStatus>): JSONArray {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject()
                    .put("name", item.name)
                    .put("url", item.url)
                    .put("state", when (item.state) {
                        EndpointStatus.State.OK -> "ok"
                        EndpointStatus.State.FAIL -> "fail"
                        EndpointStatus.State.UNKNOWN -> "unknown"
                    })
                    .put("detail", item.detail)
            )
        }
        return arr
    }

    private fun errorJson(message: String): String =
        JSONObject().put("ok", false).put("error", message).toString()

    private fun parseSaveBody(body: String): Pair<List<EndpointInput>, List<EndpointInput>> {
        val json = JSONObject(body)
        return parseEntries(json.optJSONArray("vod")) to parseEntries(json.optJSONArray("live"))
    }

    private fun parseEntries(array: JSONArray?): List<EndpointInput> {
        if (array == null) return emptyList()
        val out = mutableListOf<EndpointInput>()
        for (i in 0 until minOf(array.length(), MAX_ENTRIES)) {
            val obj = array.optJSONObject(i) ?: continue
            val url = obj.optString("url").trim()
            if (url.isEmpty()) continue
            if (!url.startsWith("http://") && !url.startsWith("https://")) continue
            if (url.length > MAX_URL) continue
            val name = obj.optString("name").trim().take(24)
            out += EndpointInput(name = name, url = url)
        }
        return out
    }

    /* ==================== HTTP 基础 ==================== */

    private fun write(output: OutputStream, code: Int, contentType: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val head = buildString {
            append("HTTP/1.1 $code ${statusText(code)}\r\n")
            append("Content-Type: $contentType; charset=utf-8\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Cache-Control: no-store, no-cache\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        output.write(head.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun statusText(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        403 -> "Forbidden"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) {
                if (sb.isNotEmpty() && sb[sb.length - 1] == '\r') sb.setLength(sb.length - 1)
                return sb.toString()
            }
            if (sb.length >= 8192) return null
            sb.append(b.toChar())
        }
    }

    private fun readBody(input: InputStream, length: Int): String {
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n <= 0) break
            read += n
        }
        return String(buffer, 0, read, Charsets.UTF_8)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { pair ->
            if (pair.isBlank()) return@mapNotNull null
            val key = pair.substringBefore('=').let { URLDecoder.decode(it, "UTF-8") }
            val value = pair.substringAfter('=', "").let { URLDecoder.decode(it, "UTF-8") }
            key to value
        }.toMap()
    }

    private fun deniedPage(): String = """
        <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>链接已失效</title></head>
        <body style="margin:0;background:#07090D;color:#F2F4F8;font-family:system-ui,sans-serif;
                     display:flex;align-items:center;justify-content:center;height:100vh">
          <div style="text-align:center;padding:32px">
            <div style="font-size:44px;margin-bottom:16px">🔒</div>
            <h1 style="font-size:20px;margin:0 0 10px">链接已失效</h1>
            <p style="color:#8C96A8;font-size:14px;line-height:1.7">
              配置服务的令牌已更换。<br>请在电视上重新打开「扫码配置」，再扫一次二维码。
            </p>
          </div>
        </body></html>
    """.trimIndent()

    companion object {
        private const val TAG = "ConfigServer"
        private const val HTML = "text/html"
        private const val JSON = "application/json"
        private const val TEXT = "text/plain"
        private const val MAX_BODY = 64 * 1024
        private const val MAX_ENTRIES = 30
        private const val MAX_URL = 2048
    }
}
