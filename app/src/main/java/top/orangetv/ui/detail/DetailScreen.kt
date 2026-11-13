package top.orangetv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.orangetv.data.model.VodDetail
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.EmptyState
import top.orangetv.ui.common.TvButton
import top.orangetv.ui.common.TvFocusable
import top.orangetv.ui.common.ThinProgress
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor
import top.orangetv.util.formatTime

@Composable
fun DetailScreen(
    vm: AppViewModel,
    vodId: String,
    onBack: () -> Unit,
    onPlay: (detail: VodDetail, line: Int, episode: Int) -> Unit
) {
    var detail by remember { mutableStateOf<VodDetail?>(null) }
    var loading by remember { mutableStateOf(true) }

    // 断点来自全局 StateFlow，播放页保存后回到这里会自动刷新
    val resumes by vm.resumes.collectAsStateWithLifecycle()
    val resume = remember(resumes, vodId) { resumes.firstOrNull { it.vodId == vodId } }

    LaunchedEffect(vodId) {
        loading = true
        detail = vm.detail(vodId)
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(LocalPalette.current.tint, TvColor.Bg)
                )
            )
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalPalette.current.accent)
            }

            detail == null -> EmptyState(
                title = "没拿到详情",
                detail = "当前源可能已失效，返回后重新探活试试。",
                action = { TvButton("返回", onClick = onBack) }
            )

            else -> {
                val d = detail!!
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 56.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(44.dp)
                ) {
                    // 海报
                    Box(
                        modifier = Modifier
                            .width(300.dp)
                            .height(440.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(TvColor.SurfaceHigh)
                    ) {
                        Text(
                            text = d.summary.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TvColor.TextMuted,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = d.summary.title,
                            style = MaterialTheme.typography.displayLarge,
                            color = TvColor.Text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            d.summary.score?.let {
                                Text(
                                    String.format("%.1f", it),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalPalette.current.warm
                                )
                            }
                            Text(
                                listOfNotNull(
                                    d.summary.year?.toString(),
                                    d.summary.region,
                                    d.summary.remarks
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = TvColor.TextMuted
                            )
                        }
                        Text(
                            text = d.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = androidx.compose.ui.graphics.Color(0xFFAEB8C8),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 18.dp)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.padding(top = 26.dp, bottom = 10.dp)
                        ) {
                            val r = resume
                            if (r != null) {
                                TvButton(
                                    text = "继续观看 · 第 ${r.episode + 1} 集 ${formatTime(r.positionMs)}",
                                    onClick = { onPlay(d, r.line, r.episode) }
                                )
                                TvButton(
                                    text = "从第 1 集开始",
                                    onClick = { onPlay(d, 0, 0) },
                                    primary = false
                                )
                            } else {
                                TvButton("播放第 1 集", onClick = { onPlay(d, 0, 0) })
                            }
                            TvButton("返回", onClick = onBack, primary = false)
                        }

                        if (resume != null) {
                            ThinProgress(
                                progress = resume.fraction,
                                height = 3,
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .padding(bottom = 18.dp)
                            )
                        } else {
                            Box(Modifier.padding(bottom = 18.dp))
                        }

                        Text(
                            text = "选集 · ${d.firstLine?.episodes?.size ?: 0} 集",
                            style = MaterialTheme.typography.titleMedium,
                            color = TvColor.Text,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        val episodes = d.firstLine?.episodes.orEmpty()
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 62.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(episodes, key = { it.index }) { ep ->
                                val cur = resume
                                EpisodeCell(
                                    index = ep.index,
                                    current = cur != null && cur.episode == ep.index - 1 && cur.line == 0,
                                    onClick = { onPlay(d, 0, ep.index - 1) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCell(index: Int, current: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    TvFocusable(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        focusedScale = 1.08f,
        background = if (current) palette.accent.copy(alpha = 0.14f) else TvColor.Surface
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    focused -> palette.accent
                    current -> palette.accent
                    else -> androidx.compose.ui.graphics.Color(0xFFC6CEDC)
                }
            )
            // 上次看到的那一集，底部留一条短线
            if (current) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp)
                        .width(16.dp)
                        .height(2.dp)
                        .background(palette.accent, RoundedCornerShape(50))
                )
            }
        }
    }
}
