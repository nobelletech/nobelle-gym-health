package com.nobelle.phone.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File

/**
 * Plays a single, already-verified local exercise video. Offline-first: the
 * file must be downloaded and checksum-verified by [ExerciseVideoRepository]
 * first — this player never streams. Loops silently, with play/pause, replay
 * and a mute toggle. The caller renders the source/licence/attribution line
 * from the media manifest next to it.
 */
@Composable
fun ExerciseVideoPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var muted by remember { mutableStateOf(false) }
    val player = remember(file) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(file)))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false // minimal, deliberate controls only
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { player.playWhenReady = !player.playWhenReady }) {
                Text(if (player.playWhenReady) "Pause" else "Play")
            }
            OutlinedButton(onClick = { player.seekTo(0) }) { Text("Replay") }
            OutlinedButton(onClick = {
                muted = !muted
                player.volume = if (muted) 0f else 1f
            }) { Text(if (muted) "Unmute" else "Mute") }
        }
    }
}