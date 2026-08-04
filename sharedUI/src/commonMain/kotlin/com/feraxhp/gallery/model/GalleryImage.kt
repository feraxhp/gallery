package com.feraxhp.gallery.model

enum class MediaType {
    IMAGE, VIDEO
}

data class GalleryImage(
    val id: Long,
    val uri: String,
    val name: String,
    val dateAdded: Long,
    val type: MediaType = MediaType.IMAGE,
    val isMotionPhoto: Boolean = false,
    val duration: Long? = null
)
