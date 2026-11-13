package top.orangetv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** 创建一个与 Compose 生命周期绑定的 ExoPlayer */
@Composable
fun rememberExoPlayer(): ExoPlayer {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}

/** 播放器可观察状态。ExoPlayer 的属性不是 State，必须靠 listener 转出来 */
data class PlayerUi(
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false
)

@Composable
fun rememberPlayerUi(player: ExoPlayer): PlayerUi {
    var ui by remember(player) { mutableStateOf(PlayerUi()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui = ui.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ui = ui.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    ended = playbackState == Player.STATE_ENDED
                )
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return ui
}

/** 渲染画面，控制器自己画，所以关掉 PlayerView 自带 UI */
@Composable
fun PlayerBox(player: ExoPlayer, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                this.player = player
            }
        },
        modifier = modifier
    )
}

/** 切源播放：url 变化时重新 prepare（直播页用，点播页自己控制进度） */
@Composable
fun BindMedia(player: ExoPlayer, url: String, autoPlay: Boolean = true) {
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = autoPlay
    }
}
