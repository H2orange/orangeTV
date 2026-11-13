package top.orangetv.ui.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import top.orangetv.config.EndpointStatus
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.FilterChip
import top.orangetv.ui.common.StatusDot
import top.orangetv.ui.common.TvButton
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.Palettes
import top.orangetv.ui.theme.TvColor
import top.orangetv.util.QrCode

/**
 * 扫码配置页。
 *
 * 电视上的键盘体验太差，所以这页只做三件事：
 * 显示二维码、显示每条配置的探活状态、触发重新探活。
 * 真正的编辑都在手机上进行。
 */
@Composable
fun ConfigScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ui by vm.configUi.collectAsStateWithLifecycle()
    val themeId by vm.themeId.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(0) }
    val backReq = remember { FocusRequester() }

    BackHandler { onBack() }

    LaunchedEffect(Unit) {
        vm.openConfigPanel()
        delay(150)
        runCatching { backReq.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColor.Bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 30.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "扫码配置",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TvColor.Text
                    )
                    Text(
                        text = "手机和电视连同一个 Wi-Fi，扫码后填写地址，保存即在电视上生效",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TvColor.TextMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                TvButton(
                    text = "返回",
                    onClick = onBack,
                    primary = false,
                    modifier = Modifier.focusRequester(backReq)
                )
            }

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                QrPanel(
                    ui = ui,
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight(),
                    onStart = { vm.startConfigServer() },
                    onStop = { vm.stopConfigServer() }
                )

                EndpointPanel(
                    ui = ui,
                    tab = tab,
                    onTab = { tab = it },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onReprobe = { vm.reprobeSources() },
                    onAdd = { name, url -> vm.addEndpoint(isLive = tab == 0, name = name, url = url) }
                )
            }

            Spacer(Modifier.height(16.dp))

            ThemeRow(current = themeId, onPick = { vm.setTheme(it) })
        }
    }
}

/* ==================== 左侧：二维码 ==================== */

@Composable
private fun QrPanel(
    ui: AppViewModel.ConfigUi,
    modifier: Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val palette = LocalPalette.current
    val pageUrl = ui.pageUrl

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TvColor.Surface)
            .padding(22.dp)
    ) {
        Text(
            text = "手机扫码",
            style = MaterialTheme.typography.titleMedium,
            color = TvColor.Text
        )
        Text(
            text = "打开相机或微信扫这个码",
            style = MaterialTheme.typography.labelMedium,
            color = TvColor.TextMuted,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (pageUrl != null) {
            val bitmap = remember(pageUrl) { QrCode.bitmap(pageUrl, 560) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "配置二维码",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = pageUrl,
                style = MaterialTheme.typography.labelMedium,
                color = TvColor.TextMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "令牌只在本次运行有效，重启后需重新扫码",
                style = MaterialTheme.typography.labelMedium,
                color = TvColor.TextDim,
                modifier = Modifier.padding(top = 6.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ui.error ?: "配置服务未启动",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFEF8B8B)
                )
            }
        }

        Spacer(Modifier.weight(1f, fill = false))

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (ui.running) {
                TvButton(text = "停止服务", onClick = onStop, primary = false)
            } else {
                TvButton(text = "启动服务", onClick = onStart, primary = true)
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(ok = ui.running, unknown = !ui.running)
            Text(
                text = if (ui.running) "服务运行中 · 仅监听局域网" else "服务已停止",
                style = MaterialTheme.typography.labelMedium,
                color = if (ui.running) palette.accent else TvColor.TextDim,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/* ==================== 右侧：源列表 ==================== */

@Composable
private fun EndpointPanel(
    ui: AppViewModel.ConfigUi,
    tab: Int,
    onTab: (Int) -> Unit,
    modifier: Modifier,
    onReprobe: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    val list = if (tab == 0) ui.live else ui.vod

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(TvColor.Surface)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(text = "直播源", selected = tab == 0, onClick = { onTab(0) })
            Spacer(Modifier.width(10.dp))
            FilterChip(text = "点播源", selected = tab == 1, onClick = { onTab(1) })
            Spacer(Modifier.weight(1f))
            Text(
                text = "共 ${list.size} 条 · 在手机上编辑",
                style = MaterialTheme.typography.labelMedium,
                color = TvColor.TextDim
            )
        }

        Spacer(Modifier.height(14.dp))

        if (list.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tab == 0) {
                        "还没有配置直播源\n内置的演示频道仍然可以播放"
                    } else {
                        "还没有配置点播源\n首页暂时使用内置演示数据"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TvColor.TextMuted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(list, key = { it.url }) { item ->
                    EndpointRow(item)
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TvButton(text = "重新探活", onClick = onReprobe, primary = false)
            Text(
                text = if (ui.checkedAt > 0L) "上次检测 ${stamp(ui.checkedAt)}" else "尚未检测",
                style = MaterialTheme.typography.labelMedium,
                color = TvColor.TextDim
            )
        }

        ManualInputRow(
            isLive = tab == 0,
            onAdd = onAdd,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

/**
 * 电视端手动输入。聚焦输入框后遥控器 OK 会调出系统输入法，
 * MuMu / 手机平板直接用键盘。地址重复时服务端会静默忽略。
 */
@Composable
private fun ManualInputRow(
    isLive: Boolean,
    onAdd: (name: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(isLive) { mutableStateOf("") }
    var url by remember(isLive) { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "也可以直接在电视上输入",
            style = MaterialTheme.typography.labelLarge,
            color = TvColor.TextMuted
        )
        Row(
            modifier = Modifier
                .padding(top = 10.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称（可选）") },
                singleLine = true,
                modifier = Modifier.width(220.dp)
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(if (isLive) "M3U / TXT 播放列表地址" else "点播接口地址") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TvButton(
                text = "添加",
                enabled = url.isNotBlank(),
                onClick = {
                    onAdd(name, url)
                    name = ""
                    url = ""
                }
            )
        }
        Text(
            text = "直播源支持 M3U / 带分组的 TXT 播放列表；添加后自动探活",
            style = MaterialTheme.typography.labelMedium,
            color = TvColor.TextDim,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun EndpointRow(item: EndpointStatus) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TvColor.SurfaceHigh)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(
            ok = item.state == EndpointStatus.State.OK,
            unknown = item.state == EndpointStatus.State.UNKNOWN
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = "${item.name} · ${item.detail}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (item.state) {
                    EndpointStatus.State.OK -> TvColor.Text
                    EndpointStatus.State.FAIL -> Color(0xFFEF8B8B)
                    EndpointStatus.State.UNKNOWN -> TvColor.TextMuted
                }
            )
            Text(
                text = item.url,
                style = MaterialTheme.typography.labelMedium,
                color = TvColor.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (item.state == EndpointStatus.State.OK) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accent.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "可用",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.accent
                )
            }
        }
    }
}

/* ==================== 底部：外观 ==================== */

@Composable
private fun ThemeRow(current: String, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "外观",
            style = MaterialTheme.typography.labelLarge,
            color = TvColor.TextMuted,
            modifier = Modifier.padding(end = 14.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Palettes.forEach { palette ->
                FilterChip(
                    text = palette.label,
                    selected = palette.id == current,
                    onClick = { onPick(palette.id) }
                )
            }
        }
    }
}

private fun stamp(millis: Long): String {
    val totalSeconds = (System.currentTimeMillis() - millis) / 1000
    return when {
        totalSeconds < 5 -> "刚刚"
        totalSeconds < 60 -> "${totalSeconds} 秒前"
        totalSeconds < 3600 -> "${totalSeconds / 60} 分钟前"
        else -> "${totalSeconds / 3600} 小时前"
    }
}
