package com.feraxhp.gallery.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

data class ShatterData(
    val bitmap: ImageBitmap,
    val offset: IntOffset,
    val size: IntSize
)
