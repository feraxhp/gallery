package com.feraxhp.gallery.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaType {
    IMAGE, VIDEO
}

@Serializable
data class GalleryImage(
    val id: Long,
    val uri: String,
    val name: String,
    val dateAdded: Long,
    val type: MediaType = MediaType.IMAGE,
    val isMotionPhoto: Boolean = false,
    val duration: Long? = null,
    val width: Int = 0,
    val height: Int = 0
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}
