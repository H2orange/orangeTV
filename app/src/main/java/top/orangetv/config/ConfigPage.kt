package top.orangetv.config

import android.content.Context

/**
 * 手机端表单页的加载器。
 *
 * 页面放在 assets/config_page.html 而不是写成 Kotlin 字符串：
 * 这样可以直接在浏览器里打开核对版式，改样式也不用碰代码。
 */
class ConfigPage(private val context: Context) {

    private val cached: String by lazy {
        runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrElse { FALLBACK }
    }

    fun render(): String = cached

    private companion object {
        const val ASSET_NAME = "config_page.html"

        val FALLBACK = """
            <!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>配置页缺失</title></head>
            <body style="margin:0;background:#07090D;color:#F2F4F8;
                         font-family:system-ui,sans-serif;padding:40px 24px;line-height:1.8">
              <h1 style="font-size:18px">配置页面没有打包进来</h1>
              <p style="color:#8C96A8;font-size:14px">
                assets/config_page.html 缺失，请重新构建安装包。
              </p>
            </body></html>
        """.trimIndent()
    }
}
