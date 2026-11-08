package com.feraxhp.gallery.util

import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

@Composable
expect fun rememberVideoModel(uri: String, duration: Long?): Any?

@Composable
expect fun rememberThumbnailModel(
    uri: String,
    isVideo: Boolean = false,
    duration: Long? = null,
    sizePx: Int = 360
): Any?

@Composable
expect fun rememberFullImageModel(
    uri: String,
    isVideo: Boolean = false,
    duration: Long? = null,
    thumbnailSizePx: Int = 360
): Any?

/**
 * Extrae un ImageBitmap de un Image de Coil de forma compatible con KMP.
 */
expect fun Image.toImageBitmap(): ImageBitmap?
