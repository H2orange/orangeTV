package top.orangetv.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 一套配色。电视端固定深色底，只换主色与暖色。 */
data class TvPalette(
    val id: String,
    val label: String,
    val accent: Color,
    val onAccent: Color,
    val warm: Color,
    val tint: Color
)

val Palettes: List<TvPalette> = listOf(
    TvPalette("coral", "珊瑚橙", Color(0xFFFF8256), Color(0xFF1A0A05), Color(0xFFFFD08A), Color(0xFF1A0E0A)),
    TvPalette("aurora", "极光青", Color(0xFF5FE3C0), Color(0xFF04120E), Color(0xFFF2B36B), Color(0xFF0E1720)),
    TvPalette("gold", "琥珀金", Color(0xFFE8B25C), Color(0xFF1A1105), Color(0xFF7FD8C0), Color(0xFF171208)),
    TvPalette("ice", "冰川蓝", Color(0xFF6BA8FF), Color(0xFF05111F), Color(0xFF9FE3F0), Color(0xFF0B1320)),
    TvPalette("sakura", "樱粉", Color(0xFFF287A8), Color(0xFF1F0710), Color(0xFFFFC9A8), Color(0xFF170B11)),
    TvPalette("carmine", "胭脂红", Color(0xFFF26D6D), Color(0xFF1F0808), Color(0xFFFFC98A), Color(0xFF160B0B))
)

val DefaultPalette: TvPalette get() = Palettes.first { it.id == "coral" }

fun paletteOf(id: String?): TvPalette = Palettes.firstOrNull { it.id == id } ?: DefaultPalette

val LocalPalette = staticCompositionLocalOf { DefaultPalette }

object TvColor {
    val Bg = Color(0xFF07090D)
    val Surface = Color(0xFF12161F)
    val SurfaceHigh = Color(0xFF1A1F2B)
    val Text = Color(0xFFF2F4F8)
    val TextMuted = Color(0xFF8C96A8)
    val TextDim = Color(0xFF5C6577)
    val Divider = Color(0x17FFFFFF)
}

/** 十英尺界面的字号：比手机整体大一号，保证三米外可读 */
private val TvTypography = Typography(
    displayLarge = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
)

@Composable
fun OrangeTvTheme(
    themeId: String = "coral",
    content: @Composable () -> Unit
) {
    val palette = paletteOf(themeId)
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = palette.onAccent,
        secondary = palette.warm,
        background = TvColor.Bg,
        onBackground = TvColor.Text,
        surface = TvColor.Surface,
        onSurface = TvColor.Text,
        surfaceVariant = TvColor.SurfaceHigh,
        onSurfaceVariant = TvColor.TextMuted,
        outline = TvColor.Divider
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = TvTypography,
            content = content
        )
    }
}
