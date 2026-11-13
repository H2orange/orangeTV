package top.orangetv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.orangetv.data.model.VodSummary
import top.orangetv.data.source.SourceRegistry
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.EmptyState
import top.orangetv.ui.common.PosterRow
import top.orangetv.ui.common.TopTab
import top.orangetv.ui.common.TvButton
import top.orangetv.ui.common.TvScaffold
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenDetail: (VodSummary) -> Unit,
    onNavigate: (TopTab) -> Unit,
    onPlayFeatured: (VodSummary) -> Unit
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val state by vm.vodState.collectAsStateWithLifecycle()
    val loading by vm.homeLoading.collectAsStateWithLifecycle()

    TvScaffold(current = TopTab.Home, onNavigate = onNavigate) {
        when {
            state is SourceRegistry.VodState.Unavailable -> {
                EmptyState(
                    title = "所有点播源都不可用",
                    detail = "启动探活没有找到可用的地址。请到手机的配置页更新源地址后重试。",
                    action = { TvButton("重新探活", onClick = { vm.refreshSources() }) }
                )
            }

            loading || rows.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LocalPalette.current.accent)
                }
            }

            else -> {
                val featured = rows.firstOrNull()?.items?.firstOrNull()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp)
                ) {
                    item(key = "hero") {
                        if (featured != null) {
                            Hero(
                                item = featured,
                                onPlay = { onPlayFeatured(featured) },
                                onDetail = { onOpenDetail(featured) }
                            )
                        }
                    }
                    items(rows, key = { it.key }) { row ->
                        PosterRow(title = row.title, items = row.items, onOpen = onOpenDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hero(item: VodSummary, onPlay: () -> Unit, onDetail: () -> Unit) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        TvColor.Bg,
                        TvColor.Bg.copy(alpha = 0.86f),
                        Color.Transparent
                    )
                )
            )
    ) {
        // 右侧氛围块，代替真实背景图
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.52f)
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(bottomStart = 220.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            palette.accent.copy(alpha = 0.22f),
                            palette.tint.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp)
                .width(620.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Badge("独家首播")
                Badge("4K HDR", warm = true)
            }
            Text(
                text = item.title,
                style = MaterialTheme.typography.displayLarge,
                color = TvColor.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item.score?.let {
                    Text(
                        text = String.format("%.1f", it),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.warm
                    )
                }
                MetaText(item.year?.toString())
                MetaText(item.category)
                MetaText(item.region)
                MetaText(item.remarks)
            }
            Text(
                text = "围绕一场意外展开，人物在有限的时间里被迫做出选择。叙事克制、影像干净，结尾留白处理得相当漂亮。",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB9C2D2),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvButton("立即播放", onClick = onPlay)
                TvButton("查看详情", onClick = onDetail, primary = false)
            }
        }
    }
}

@Composable
private fun Badge(text: String, warm: Boolean = false) {
    val palette = LocalPalette.current
    val color = if (warm) palette.warm else palette.accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun MetaText(text: String?) {
    if (text.isNullOrBlank()) return
    Text(text, style = MaterialTheme.typography.bodyMedium, color = TvColor.TextMuted)
}
