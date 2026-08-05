package com.feraxhp.gallery.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun VideoPlayer(
    uri: String,
    isMotionPhoto: Boolean = false,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
    onProgressUpdate: (Long) -> Unit = {},
    onVideoClick: () -> Unit = {}
)
