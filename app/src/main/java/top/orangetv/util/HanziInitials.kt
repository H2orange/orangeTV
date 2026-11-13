package top.orangetv.util

import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * 汉字转拼音首字母，用于遥控器搜索（TKZC -> 天空之城）。
 *
 * 实现基于系统自带的 ICU 音译器（Android 10+），不引入第三方库。
 * 低版本系统上直接返回空串，搜索会退化成中文子串匹配，不会崩。
 *
 * 注意：这是兜底实现。正式接入数据源时，首字母应当在入库/索引阶段
 * 一次性算好存下来（几千条数据在端上算会很慢）。低版本机型建议引入
 * TinyPinyin 替换本实现。
 */
object HanziInitials {

    private val cache = ConcurrentHashMap<String, String>()

    private val transliterator: android.icu.text.Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII")
            }.getOrNull()
        } else {
            null
        }
    }

    fun of(text: String): String {
        if (text.isEmpty()) return ""
        cache[text]?.let { return it }
        val computed = compute(text)
        cache[text] = computed
        return computed
    }

    private fun compute(text: String): String {
        val engine = transliterator ?: return ""
        val latin = runCatching { engine.transliterate(text) }.getOrNull() ?: return ""
        val sb = StringBuilder(latin.length / 2)
        var atTokenStart = true
        for (ch in latin) {
            when {
                ch == ' ' || ch == '-' || ch == '\'' -> atTokenStart = true
                atTokenStart && (ch.isLetterOrDigit()) -> {
                    sb.append(ch.uppercaseChar())
                    atTokenStart = false
                }
                else -> atTokenStart = false
            }
        }
        return sb.toString()
    }
}
