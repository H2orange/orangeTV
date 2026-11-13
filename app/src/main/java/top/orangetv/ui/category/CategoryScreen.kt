package top.orangetv.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.orangetv.data.model.SortOrder
import top.orangetv.data.model.VodSummary
import top.orangetv.data.source.SourceRegistry
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.FilterChip
import top.orangetv.ui.common.PosterCard
import top.orangetv.ui.common.TopTab
import top.orangetv.ui.common.TvScaffold
import top.orangetv.ui.theme.TvColor

private val TYPES = listOf("全部", "电影", "剧集", "综艺", "动漫", "短剧")
private val REGIONS = listOf("全部", "大陆", "中国香港", "中国台湾", "日本", "韩国", "欧美")
private val YEARS = listOf("全部", "2026", "2025", "2024", "2023")
private val SORTS = listOf("最新", "评分", "年份")

@Composable
fun CategoryScreen(
    vm: AppViewModel,
    onOpenDetail: (VodSummary) -> Unit,
    onNavigate: (TopTab) -> Unit
) {
    var type by remember { mutableStateOf("全部") }
    var region by remember { mutableStateOf("全部") }
    var year by remember { mutableStateOf("全部") }
    var sort by remember { mutableStateOf("最新") }
    var items by remember { mutableStateOf<List<VodSummary>>(emptyList()) }

    // 分类项由数据源提供：苹果 CMS 源会给「动作片/国产剧/欧美剧」这种真实分类，
    // 拿不到才退回内置的粗分类（演示源走这条路）。
    var types by remember { mutableStateOf(TYPES) }
    val vodState by vm.vodState.collectAsStateWithLifecycle()
    // 分类表要等探活完成才有，所以跟着 Ready 状态刷新
    LaunchedEffect(vodState is SourceRegistry.VodState.Ready) {
        if (vodState is SourceRegistry.VodState.Ready) {
            val dynamic = vm.categories()
            if (dynamic.isNotEmpty()) {
                types = listOf("全部") + dynamic.map { it.name }.distinct()
                if (type !in types) type = "全部"
            }
        }
    }

    LaunchedEffect(type, region, year, sort) {
        items = vm.browse(
            category = type.takeIf { it != "全部" },
            region = region.takeIf { it != "全部" },
            year = year.takeIf { it != "全部" },
            sort = when (sort) {
                "评分" -> SortOrder.Score
                "年份" -> SortOrder.Year
                else -> SortOrder.Latest
            }
        )
    }

    // 整页一个滚动容器：筛选区作为通栏 header，卡片区域天然占满剩余空间。
    // 之前把筛选区和网格塞进固定 Column，两者争高度，矮屏上网格会被挤成一条。
    TvScaffold(current = TopTab.Category, onNavigate = onNavigate) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 190.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 22.dp, bottom = 40.dp)
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "分类筛选",
                        style = MaterialTheme.typography.headlineLarge,
                        color = TvColor.Text
                    )
                    Text(
                        text = "共 ${items.size} 部",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColor.TextDim,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )
                    FilterRow("类型", types, type) { type = it }
                    FilterRow("地区", REGIONS, region) { region = it }
                    FilterRow("年份", YEARS, year) { year = it }
                    FilterRow("排序", SORTS, sort) { sort = it }
                    Spacer(Modifier.height(2.dp))
                }
            }
            items(items, key = { it.id }) { item ->
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

@Composable
private fun FilterRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = TvColor.TextDim,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(options) { option ->
                FilterChip(
                    text = option,
                    selected = option == selected,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}
