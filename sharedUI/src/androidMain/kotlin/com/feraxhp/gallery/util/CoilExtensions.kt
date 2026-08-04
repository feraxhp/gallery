package com.feraxhp.gallery.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.video.videoFrameMillis

@Composable
actual fun rememberVideoModel(uri: String, duration: Long?): Any? {
    val context = LocalPlatformContext.current
    return remember(uri, duration, context) {
        ImageRequest.Builder(context)
            .data(uri)
            .videoFrameMillis(if (duration != null && duration > 0) {
                minOf(1000L, duration / 2)
            } else {
                1000L
            })
            .build()
    }
}
