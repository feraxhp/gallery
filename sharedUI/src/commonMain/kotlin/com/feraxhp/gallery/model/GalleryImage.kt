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
    val height: Int = 0,
    val albumId: String? = null,
    val albumName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val shutterSpeed: String? = null,
    val dateTaken: Long? = null,
    val cameraModel: String? = null,
    val cameraManufacturer: String? = null,
    val iso: String? = null,
    val aperture: String? = null,
    val focalLength: String? = null,
    val size: Long? = null,
    val software: String? = null,
    val path: String? = null
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}
