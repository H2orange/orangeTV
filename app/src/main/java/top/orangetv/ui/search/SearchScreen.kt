package top.orangetv.ui.search

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
import androidx.compose.ui.unit.dp
import top.orangetv.data.model.VodSummary
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.PosterCard
import top.orangetv.ui.common.TopTab
import top.orangetv.ui.common.TvFocusable
import top.orangetv.ui.common.TvScaffold
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor

private val KEYS = (('A'..'Z').map { it.toString() }) + listOf("⌫", "清空")

@Composable
fun SearchScreen(
    vm: AppViewModel,
    onOpenDetail: (VodSummary) -> Unit,
    onNavigate: (TopTab) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<VodSummary>>(emptyList()) }

    LaunchedEffect(query) {
        results = if (query.isBlank()) emptyList() else vm.search(query)
    }

    TvScaffold(current = TopTab.Search, onNavigate = onNavigate) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Column(modifier = Modifier.width(760.dp)) {
                Text(
                    text = "搜索",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TvColor.Text
                )
                Text(
                    text = "支持拼音首字母，例：TKZC → 天空之城",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColor.TextDim,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )

                QueryBox(query)

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 20.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(KEYS) { key ->
                        KeyCap(key) {
                            query = when (key) {
                                "⌫" -> query.dropLast(1)
                                "清空" -> ""
                                else -> if (query.length < 8) query + key else query
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = if (query.isBlank()) "全部内容" else "找到 ${results.size} 个结果",
                    style = MaterialTheme.typography.titleLarge,
                    color = TvColor.Text,
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    items(results, key = { it.id }) { item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpenDetail(item) },
                            modifier = Modifier.fillMaxWidth(),
                            width = 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryBox(query: String) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(TvColor.Surface)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (query.isBlank()) "输入拼音首字母" else query,
                style = if (query.isBlank()) {
                    MaterialTheme.typography.bodyLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                color = if (query.isBlank()) TvColor.TextDim else TvColor.Text
            )
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(3.dp)
                    .height(30.dp)
                    .background(palette.accent)
            )
        }
    }
}

@Composable
private fun KeyCap(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    TvFocusable(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        focusedScale = 1.07f,
        background = TvColor.Surface
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = if (label.length > 1) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = if (focused) palette.accent else Color0xD2D9E6
            )
        }
    }
}

private val Color0xD2D9E6 = androidx.compose.ui.graphics.Color(0xFFD2D9E6)
