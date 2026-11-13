package top.orangetv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor

enum class TopTab(val route: String, val label: String) {
    Home("home", "首页"),
    Live("live", "直播"),
    Category("category", "分类"),
    Search("search", "搜索")
}

/**
 * 顶栏齿轮的动作。
 *
 * 用 CompositionLocal 而不是往每个页面加参数：四个屏都要同一个入口，
 * 加参数会把 4 个文件全都改一遍，还容易漏。
 */
val LocalOpenConfig = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * 电视端统一外框：顶栏 + 内容区。
 * 内容区不加 padding，各页面自己决定是整屏大图还是留白列表。
 */
@Composable
fun TvScaffold(
    current: TopTab,
    onNavigate: (TopTab) -> Unit,
    content: @Composable () -> Unit
) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColor.Bg)
    ) {
        // 顶部氛围光
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(palette.accent.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(current = current, onNavigate = onNavigate)
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
fun TopBar(current: TopTab, onNavigate: (TopTab) -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 品牌
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accent),
            contentAlignment = Alignment.Center
        ) {
            Text("O", color = palette.onAccent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Text(
            text = "Orange TV",
            style = MaterialTheme.typography.titleMedium,
            color = TvColor.Text,
            modifier = Modifier.padding(start = 10.dp, end = 26.dp)
        )

        TopTab.entries.forEach { tab ->
            NavTab(
                label = tab.label,
                selected = tab == current,
                onClick = { onNavigate(tab) }
            )
        }

        Spacer(Modifier.weight(1f))

        ConfigButton()
    }
}

/** 顶栏右侧的扫码配置入口 */
@Composable
private fun ConfigButton() {
    val palette = LocalPalette.current
    val openConfig = LocalOpenConfig.current
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) palette.accent.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = openConfig),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = "扫码配置",
            tint = if (focused) TvColor.Text else TvColor.TextMuted,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(
                when {
                    focused -> palette.accent.copy(alpha = 0.18f)
                    selected -> Color(0x0FFFFFFF)
                    else -> Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                focused || selected -> TvColor.Text
                else -> TvColor.TextMuted
            }
        )
    }
}
