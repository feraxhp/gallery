package com.feraxhp.gallery.util

import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

@Composable
expect fun rememberVideoModel(uri: String, duration: Long?): Any?

/**
 * Extrae un ImageBitmap de un Image de Coil de forma compatible con KMP.
 */
expect fun Image.toImageBitmap(): ImageBitmap?
