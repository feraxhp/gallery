package com.feraxhp.gallery.util

import androidx.compose.runtime.Composable

@Composable
expect fun rememberVideoModel(uri: String, duration: Long?): Any?
