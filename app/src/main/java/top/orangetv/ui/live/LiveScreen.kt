package top.orangetv.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.orangetv.data.model.Channel
import top.orangetv.data.source.SourceRegistry
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.EmptyState
import top.orangetv.ui.common.FilterChip
import top.orangetv.ui.common.TopTab
import top.orangetv.ui.common.TvButton
import top.orangetv.ui.common.TvFocusable
import top.orangetv.ui.common.TvScaffold
import top.orangetv.ui.player.BindMedia
import top.orangetv.ui.player.PlayerBox
import top.orangetv.ui.player.rememberExoPlayer
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor

@Composable
fun LiveScreen(vm: AppViewModel, onNavigate: (TopTab) -> Unit) {
    val state by vm.liveState.collectAsStateWithLifecycle()
    var group by remember { mutableStateOf("全部") }
    var current by remember { mutableStateOf<Channel?>(null) }

    val channels = (state as? SourceRegistry.LiveState.Ready)?.channels.orEmpty()
    val groups = remember(channels) { listOf("全部") + channels.map { it.group }.distinct() }
    val filtered = remember(channels, group) {
        if (group == "全部") channels else channels.filter { it.group == group }
    }

    LaunchedEffect(filtered) {
        if (current == null || filtered.none { it.id == current?.id }) {
            current = filtered.firstOrNull()
        }
    }

    TvScaffold(current = TopTab.Live, onNavigate = onNavigate) {
        when (state) {
            is SourceRegistry.LiveState.Probing -> Box(Modifier.fillMaxSize())

            is SourceRegistry.LiveState.Unavailable -> EmptyState(
                title = "还没有可用的直播源",
                detail = "在手机上打开配置页，粘贴你的 M3U 地址后保存，电视端会自动同步。内置演示频道也可直接观看。",
                action = { TvButton("重新探活", onClick = { vm.refreshSources() }) }
            )

            else -> Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp)
            ) {
                ChannelColumn(
                    groups = groups,
                    group = group,
                    onGroup = { group = it },
                    channels = filtered,
                    current = current,
                    onSelect = { current = it }
                )
                Column(
                    modifier = Modifier
                        .padding(start = 32.dp)
                        .fillMaxSize()
                ) {
                    LiveStage(current)
                }
            }
        }
    }
}

@Composable
private fun ChannelColumn(
    groups: List<String>,
    group: String,
    onGroup: (String) -> Unit,
    channels: List<Channel>,
    current: Channel?,
    onSelect: (Channel) -> Unit
) {
    Column(modifier = Modifier.width(370.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(groups) { g ->
                FilterChip(text = g, selected = g == group, onClick = { onGroup(g) })
            }
        }
        Column(modifier = Modifier.height(18.dp)) {}
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(channels, key = { it.id }) { ch ->
                ChannelItem(ch, ch.id == current?.id) { onSelect(ch) }
            }
        }
    }
}

@Composable
private fun ChannelItem(ch: Channel, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    TvFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        focusedScale = 1.02f,
        shape = RoundedCornerShape(11.dp),
        background = if (selected) palette.accent.copy(alpha = 0.10f) else TvColor.Surface
    ) { focused ->
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = ch.name,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    focused -> TvColor.Text
                    selected -> palette.accent
                    else -> TvColor.Text
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ch.group,
                style = MaterialTheme.typography.labelMedium,
                color = TvColor.TextDim
            )
        }
    }
}

@Composable
private fun LiveStage(channel: Channel?) {
    val palette = LocalPalette.current
    val player = rememberExoPlayer()
    BindMedia(player, channel?.url.orEmpty())

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TvColor.Surface)
        ) {
            PlayerBox(player, Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(palette.accent.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onAccent
                )
            }
        }
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                text = channel?.name ?: "未选择频道",
                style = MaterialTheme.typography.headlineMedium,
                color = TvColor.Text
            )
            Text(
                text = "按遥控器左右键切换分组，上下键选频道",
                style = MaterialTheme.typography.bodyMedium,
                color = TvColor.TextDim,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
