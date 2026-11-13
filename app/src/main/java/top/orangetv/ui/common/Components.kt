package top.orangetv.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.orangetv.data.model.VodSummary
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor

/**
 * 电视端通用可聚焦容器。
 *
 * 用 onFocusChanged 自己做焦点态，不依赖 Material 的 ripple，
 * 这样遥控器焦点环在深色背景上才够清晰。
 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusedScale: Float = 1.05f,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    focusedBorder: @Composable () -> Color = { LocalPalette.current.accent },
    background: Color = TvColor.Surface,
    enabled: Boolean = true,
    content: @Composable (focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, label = "focusScale")
    val borderColor = if (focused) focusedBorder() else Color.Transparent

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(background)
            .border(if (focused) 2.dp else 0.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        content(focused)
    }
}

/** 主按钮 / 次按钮 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true
) {
    val palette = LocalPalette.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        background = if (primary) palette.accent else Color(0x17FFFFFF),
        focusedScale = if (primary) 1.06f else 1.04f,
        enabled = enabled
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (!enabled) {
                TvColor.TextMuted
            } else if (primary) palette.onAccent else TvColor.Text,
            modifier = Modifier.padding(horizontal = 30.dp, vertical = 14.dp)
        )
    }
}

/** 筛选 chip */
@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    TvFocusable(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        focusedScale = 1.06f,
        background = if (selected) palette.accent.copy(alpha = 0.16f) else Color(0x0FFFFFFF)
    ) { focused ->
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = when {
                focused -> TvColor.Text
                selected -> palette.accent
                else -> Color(0xFFC1C9D7)
            },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/** 封面占位：没有图片时按标题哈希生成渐变，避免大片空白 */
private fun placeholderBrush(key: String): Brush {
    val hue = (key.hashCode() and 0xFF) / 255f * 360f
    val a = Color.hsl(hue, 0.32f, 0.20f)
    val b = Color.hsl((hue + 32f) % 360f, 0.28f, 0.09f)
    return Brush.linearGradient(listOf(a, b))
}

@Composable
fun PosterCard(
    item: VodSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Int = 186
) {
    val palette = LocalPalette.current
    val sizeModifier = if (width > 0) Modifier.width(width.dp) else Modifier.fillMaxWidth()
    TvFocusable(
        onClick = onClick,
        modifier = modifier.then(sizeModifier),
        focusedScale = 1.07f
    ) { focused ->
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(placeholderBrush(item.title))
            ) {
                if (item.cover != null) {
                    AsyncImage(
                        model = item.cover,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                if (!item.remarks.isNullOrBlank()) {
                    Text(
                        text = item.remarks,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0xB306080C), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) palette.accent else TvColor.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

/** 横向内容行 */
@Composable
fun PosterRow(
    title: String,
    items: List<VodSummary>,
    onOpen: (VodSummary) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TvColor.Text,
            modifier = Modifier.padding(start = 56.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(items, key = { it.id }) { item ->
                PosterCard(item = item, onClick = { onOpen(item) })
            }
        }
    }
}

/** 细进度条 */
@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Int = 4
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0x33FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height.dp)
                .background(palette.accent)
        )
    }
}

/** 状态点 */
@Composable
fun StatusDot(ok: Boolean, unknown: Boolean = false) {
    val color = when {
        unknown -> Color(0xFF7C8698)
        ok -> Color(0xFF4ADE80)
        else -> Color(0xFFEF6B6B)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, RoundedCornerShape(50))
    )
}

/** 空态 / 错误态 */
@Composable
fun EmptyState(title: String, detail: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.padding(40.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = TvColor.Text)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = TvColor.TextMuted)
        action?.invoke()
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TvColor.Divider)
    )
}

@Composable
fun AspectBox(ratio: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.aspectRatio(ratio)) { content() }
}

@Composable
fun HSpacer(w: Int) = Box(Modifier.width(w.dp))
