package com.feraxhp.gallery.components

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
actual fun VideoPlayer(
    uri: String,
    isMotionPhoto: Boolean,
    isPlaying: Boolean,
    modifier: Modifier,
    onProgressUpdate: (Long) -> Unit,
    onVideoClick: () -> Unit
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(android.net.Uri.parse(uri))
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = isPlaying
            repeatMode = Player.REPEAT_MODE_ONE
            
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("VideoPlayer", "Error playing $uri: ${error.message}", error)
                }
            })
        }
    }

    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    var progress by remember { mutableFloatStateOf(0f) }
    var isBuffering by remember { mutableStateOf(true) }
    var isReadyToShow by remember { mutableStateOf(false) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            if (exoPlayer.duration > 0) {
                progress = exoPlayer.currentPosition.toFloat() / exoPlayer.duration
                onProgressUpdate(exoPlayer.currentPosition)
            }
            isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING
            if (exoPlayer.playbackState == Player.STATE_READY) {
                isReadyToShow = true
            }
            delay(16)
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (isReadyToShow) 1f else 0f
                }
        )

        // Progress Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            if (progress > 0f || !isBuffering) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}
