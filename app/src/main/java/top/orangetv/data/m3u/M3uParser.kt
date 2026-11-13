package top.orangetv.data.m3u

/**
 * M3U / TXT 播放列表解析。
 *
 * 兼容三种常见格式：
 * 1. 标准 M3U：`#EXTINF:-1 tvg-logo="..." group-title="央视",CCTV-1 综合`
 * 2. 带分组标记的 TXT：`央视频道,#genre#` 后面跟 `CCTV-1,http://...`
 * 3. 纯 TXT：每行 `频道名,http://...`
 */
object M3uParser {

    data class Entry(
        val name: String,
        val group: String,
        val logo: String?,
        val url: String
    )

    private val ATTR_REGEX = Regex("""([\w-]+)="([^"]*)"""")
    private val GENRE_REGEX = Regex("""^(.*?),#genre#\s*$""")
    private val PLAIN_TXT_REGEX = Regex("""^([^,]+?)\s*,\s*(https?://\S+)\s*$""")

    fun parse(content: String): List<Entry> {
        val out = ArrayList<Entry>(256)
        val lines = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }

        var pendingName: String? = null
        var pendingGroup = "未分组"
        var pendingLogo: String? = null
        var currentGroup = "未分组"

        for (line in lines) {
            when {
                line.startsWith("#EXTM3U") -> Unit

                line.startsWith("#EXTINF") -> {
                    val attrs = ATTR_REGEX.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: currentGroup
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    // 频道名在最后一个逗号之后
                    val comma = line.lastIndexOf(',')
                    pendingName = if (comma >= 0 && comma < line.length - 1) {
                        line.substring(comma + 1).trim()
                    } else {
                        attrs["tvg-name"]
                    }?.takeIf { it.isNotBlank() }
                }

                line.startsWith("#") -> Unit // #EXTGRP 等标记忽略

                GENRE_REGEX.matches(line) -> {
                    currentGroup = GENRE_REGEX.matchEntire(line)!!.groupValues[1].trim()
                    pendingName = null
                }

                else -> {
                    val url = line
                    if (!url.startsWith("http", ignoreCase = true)) continue

                    val name = pendingName
                    if (name != null) {
                        out += Entry(
                            name = name,
                            group = pendingGroup.ifBlank { currentGroup },
                            logo = pendingLogo,
                            url = url
                        )
                        pendingName = null
                        pendingLogo = null
                    } else {
                        // 纯 TXT：频道名,url
                        val m = PLAIN_TXT_REGEX.matchEntire(line)
                        val plainName = m?.groupValues?.get(1)?.trim()
                        out += Entry(
                            name = plainName?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/'),
                            group = currentGroup,
                            logo = null,
                            url = url
                        )
                    }
                }
            }
        }
        return out
    }

    /** 从分组列表里抽出分组名，保持首次出现顺序 */
    fun groups(entries: List<Entry>): List<String> =
        entries.map { it.group }.distinct()
}
