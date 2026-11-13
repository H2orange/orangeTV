package top.orangetv.ui.player

import android.view.KeyEvent as AndroidKey
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import top.orangetv.ui.AppViewModel
import top.orangetv.ui.common.FilterChip
import top.orangetv.ui.common.ThinProgress
import top.orangetv.ui.common.TvButton
import top.orangetv.ui.theme.LocalPalette
import top.orangetv.ui.theme.TvColor
import top.orangetv.util.formatTime
import top.orangetv.util.speedLabel

/** 可选倍速。电视端看剧主要用 1x–2x，留一个 0.5x 给慢放 */
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 小于这个进度不记断点，避免刚点开就留一条「看到第 3 秒」 */
private const val RESUME_MIN_MS = 20_000L

/** 距片尾这么近视为看完，清掉断点 */
private const val RESUME_TAIL_MS = 45_000L

/** 播放中每 5 秒落一次盘，异常退出最多丢 5 秒 */
private const val SAVE_EVERY_MS = 5_000L

/** 遥控器连按的步进：单次 10s，按住 3 次后 30s，8 次后 60s */
private fun stepOf(repeat: Int): Long = when {
    repeat >= 8 -> 60_000L
    repeat >= 3 -> 30_000L
    else -> 10_000L
}

@Composable
fun PlayerScreen(vm: AppViewModel, onBack: () -> Unit) {
    val playback by vm.playback.collectAsStateWithLifecycle()
    val player = rememberExoPlayer()
    val ui = rememberPlayerUi(player)

    var speed by remember { mutableStateOf(vm.speed) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var resumeHint by remember(playback?.url) { mutableStateOf(false) }
    var resumeApplied by remember(playback?.url) { mutableStateOf(false) }

    var seekDelta by remember { mutableLongStateOf(0L) }
    var seekAt by remember { mutableLongStateOf(0L) }

    var controlsVisible by remember { mutableStateOf(true) }
    var lastAction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingFocus by remember { mutableStateOf(false) }

    var videoFocused by remember { mutableStateOf(false) }
    var chipFocused by remember { mutableStateOf(false) }
    var btnFocused by remember { mutableStateOf(false) }

    val videoReq = remember { FocusRequester() }
    val btnReq = remember { FocusRequester() }
    val retryReq = remember { FocusRequester() }

    // 进入播放页时读一次断点
    val resumeMs = remember(playback?.url) {
        val p = playback
        if (p != null && p.rememberProgress) vm.resumeOf(p.key)?.positionMs ?: 0L else 0L
    }
    val resumeTarget = rememberUpdatedState(resumeMs)

    val accent = LocalPalette.current.accent

    /* ---------------- 内部动作 ---------------- */

    fun touch() {
        lastAction = System.currentTimeMillis()
        controlsVisible = true
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
        touch()
    }

    fun seekTo(target: Long) {
        val dur = player.duration
        val t = if (dur > 0L) target.coerceIn(0L, dur) else target.coerceAtLeast(0L)
        player.seekTo(t)
        position = t
        touch()
    }

    fun seekBy(delta: Long) {
        val from = player.currentPosition.coerceAtLeast(0L)
        seekTo(from + delta)
        seekDelta = if (delta >= 0) delta else -delta
        seekAt = System.currentTimeMillis()
    }

    /** 落盘当前进度。媒体未就绪时不写，避免把 0 覆盖掉已有断点 */
    fun saveNow() {
        val p = playback ?: return
        if (!p.rememberProgress || !ready) return
        val dur = player.duration
        if (dur <= 0L) return
        val pos = player.currentPosition.coerceAtLeast(0L)
        when {
            pos >= dur - RESUME_TAIL_MS -> vm.forgetProgress(p.key)
            pos < RESUME_MIN_MS -> vm.forgetProgress(p.key)
            else -> vm.saveProgress(p, pos, dur)
        }
    }

    /* ---------------- 播放核心 ---------------- */

    // 倍速：进页面套用记忆值，改动即时生效并写回
    LaunchedEffect(speed) {
        player.setPlaybackParameters(player.playbackParameters.withSpeed(speed))
        vm.speed = speed
    }

    LaunchedEffect(player, playback?.url) {
        val url = playback?.url
        if (url.isNullOrBlank()) return@LaunchedEffect
        error = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player, playback?.url) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        ready = true
                        // 断点续播只在第一次就绪时做
                        if (!resumeApplied) {
                            resumeApplied = true
                            val target = resumeTarget.value
                            val dur = player.duration
                            if (target >= RESUME_MIN_MS && (dur <= 0L || target < dur - RESUME_TAIL_MS)) {
                                player.seekTo(target)
                                resumeHint = true
                            }
                        }
                    }

                    Player.STATE_ENDED -> {
                        playback?.let { if (it.rememberProgress) vm.forgetProgress(it.key) }
                    }
                }
            }

            override fun onPlayerError(e: PlaybackException) {
                error = describeError(e)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) saveNow()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 定时落盘
    LaunchedEffect(player, playback?.key) {
        while (true) {
            delay(SAVE_EVERY_MS)
            if (player.isPlaying) saveNow()
        }
    }

    // 进度条刷新
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(400)
        }
    }

    // 焦点落点：正常时给画面（方向键才能直接生效），出错时给「重试」
    LaunchedEffect(error) {
        delay(80)
        if (error == null) runCatching { videoReq.requestFocus() }
        else runCatching { retryReq.requestFocus() }
    }

    // 控件隐藏时按「下」，等控件组合出来再落焦点
    LaunchedEffect(controlsVisible, pendingFocus) {
        if (pendingFocus && controlsVisible) {
            delay(60)
            runCatching { btnReq.requestFocus() }
            pendingFocus = false
        }
    }

    // 播放中 4.5 秒无操作自动收起控件；有控件握着焦点时永不收起
    LaunchedEffect(ui.isPlaying, chipFocused, btnFocused, controlsVisible) {
        while (controlsVisible && ui.isPlaying && !chipFocused && !btnFocused) {
            delay(400)
            if (System.currentTimeMillis() - lastAction > 4500L) controlsVisible = false
        }
    }

    // 续播提示 6 秒后自动消失
    LaunchedEffect(resumeHint) {
        if (resumeHint) {
            delay(6000)
            resumeHint = false
        }
    }

    BackHandler {
        saveNow()
        player.pause()
        onBack()
    }

    // 退到后台：暂停并落盘，避免电视上按 Home 后声音还在放
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveNow()
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    /* ---------------- 遥控器 ---------------- */

    fun handleKey(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        val inControls = chipFocused || btnFocused
        // 用平台 keyCode 判断，遥控器硬按键与盒子厂的差异都在这一层吸收
        return when (e.nativeKeyEvent.keyCode) {
            AndroidKey.KEYCODE_MEDIA_PLAY_PAUSE,
            AndroidKey.KEYCODE_MEDIA_PLAY,
            AndroidKey.KEYCODE_MEDIA_PAUSE,
            AndroidKey.KEYCODE_SPACE -> {
                togglePlay(); true
            }

            AndroidKey.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekBy(30_000); true
            }

            AndroidKey.KEYCODE_MEDIA_REWIND -> {
                seekBy(-30_000); true
            }

            // 焦点在控件上时，左右交给系统做焦点移动
            AndroidKey.KEYCODE_DPAD_LEFT -> if (inControls) {
                touch(); false
            } else {
                seekBy(-stepOf(e.nativeKeyEvent.repeatCount)); true
            }

            AndroidKey.KEYCODE_DPAD_RIGHT -> if (inControls) {
                touch(); false
            } else {
                seekBy(stepOf(e.nativeKeyEvent.repeatCount)); true
            }

            AndroidKey.KEYCODE_DPAD_UP -> if (chipFocused) {
                runCatching { videoReq.requestFocus() }
                touch(); true
            } else {
                touch(); false
            }

            AndroidKey.KEYCODE_DPAD_DOWN -> {
                touch()
                // 出错时不抢焦点，否则「重试」按钮会失焦
                if (error == null) pendingFocus = true
                true
            }

            AndroidKey.KEYCODE_DPAD_CENTER,
            AndroidKey.KEYCODE_ENTER,
            AndroidKey.KEYCODE_NUMPAD_ENTER -> {
                if (!controlsVisible) {
                    touch(); true
                } else if (videoFocused) {
                    togglePlay(); true
                } else {
                    touch(); false
                }
            }

            else -> false
        }
    }

    /* ---------------- 界面 ---------------- */

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { handleKey(it) }
    ) {
        PlayerBox(player, Modifier.fillMaxSize())

        // 全屏焦点靶：透明，只负责接管方向键
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(videoReq)
                .onFocusChanged { videoFocused = it.isFocused }
                .focusable()
        )

        if (ui.buffering && error == null) {
            CircularProgressIndicator(
                color = accent,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 快进/快退反馈
        if (seekAt > 0L && System.currentTimeMillis() - seekAt < 1400L) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xCC0A0E14))
                    .padding(horizontal = 26.dp, vertical = 14.dp)
            ) {
                Text(
                    text = (if (seekDelta > 0) "快进 " else "快退 ") +
                        "${seekDelta / 1000}s · ${formatTime(position)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TvColor.Text
                )
            }
        }

        // 续播提示
        if (resumeHint) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xB306080C))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "已从 ${formatTime(resumeMs)} 继续播放 · ←/→ 可调进度",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
        }

        // 底部控件
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
                    .padding(horizontal = 56.dp, vertical = 30.dp)
            ) {
                Text(
                    text = playback?.title ?: "未选择内容",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TvColor.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        playback?.subtitle?.takeIf { it.isNotBlank() },
                        formatTime(position) + " / " + formatTime(duration)
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColor.TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                ThinProgress(
                    progress = if (duration > 0L) position.toFloat() / duration else 0f,
                    height = 5
                )

                // 倍速
                Row(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .onFocusChanged { chipFocused = it.hasFocus },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "倍速",
                        style = MaterialTheme.typography.labelMedium,
                        color = TvColor.TextMuted,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    SPEEDS.forEach { s ->
                        FilterChip(
                            text = speedLabel(s),
                            selected = kotlin.math.abs(s - speed) < 0.01f,
                            onClick = {
                                speed = s
                                touch()
                            }
                        )
                    }
                }

                // 操作
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .onFocusChanged { btnFocused = it.hasFocus },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvButton(
                        text = if (ui.isPlaying) "暂停" else "播放",
                        onClick = { togglePlay() },
                        modifier = Modifier.focusRequester(btnReq)
                    )
                    TvButton(
                        text = "« 10s",
                        primary = false,
                        onClick = { seekBy(-10_000) }
                    )
                    TvButton(
                        text = "10s »",
                        primary = false,
                        onClick = { seekBy(10_000) }
                    )
                    TvButton(
                        text = "从头",
                        primary = false,
                        onClick = {
                            playback?.let { if (it.rememberProgress) vm.forgetProgress(it.key) }
                            seekTo(0L)
                        }
                    )
                    TvButton(
                        text = "返回",
                        primary = false,
                        onClick = {
                            saveNow()
                            player.pause()
                            onBack()
                        }
                    )
                }
            }
        } else {
            // 控件收起时给一条极淡的进度提示，避免完全不知道播到哪
            ThinProgress(
                progress = if (duration > 0L) position.toFloat() / duration else 0f,
                height = 3,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }

        // 左上角返回提示
        if (controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "返回键退出 · OK 暂停/继续",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent
                )
            }
        }

        // 播放失败
        error?.let { msg ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("播放失败", style = MaterialTheme.typography.headlineMedium, color = TvColor.Text)
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TvColor.TextMuted,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 22.dp)
                ) {
                    TvButton("重试", onClick = {
                        error = null
                        // 已在这次播放中途，不要再套一遍旧断点，就地续播
                        resumeApplied = true
                        player.prepare()
                        player.play()
                    }, modifier = Modifier.focusRequester(retryReq))
                    TvButton("返回", primary = false, onClick = {
                        saveNow()
                        player.pause()
                        onBack()
                    })
                }
            }
        }
    }
}

private fun describeError(e: PlaybackException): String = when (e.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败，检查网络后重试"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "连接超时，源可能不稳定"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器返回异常状态码，播放地址可能已失效"
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "文件不存在，播放地址可能已失效"
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "媒体格式无法解析"
    else -> e.errorCodeName + "：" + e.message.orEmpty().take(80)
}
