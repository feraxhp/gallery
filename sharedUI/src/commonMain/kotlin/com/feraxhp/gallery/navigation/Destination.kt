package com.feraxhp.gallery.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination {
    @Serializable
    data object Permissions : Destination
    @Serializable
    data object Gallery : Destination
    @Serializable
    data object Albums : Destination
    @Serializable
    data class AlbumGallery(val albumId: String, val albumName: String) : Destination
    @Serializable
    data class Detail(
        val image: com.feraxhp.gallery.model.GalleryImage,
        val allImages: List<com.feraxhp.gallery.model.GalleryImage>
    ) : Destination
    @Serializable
    data class MoveToAlbum(
        val image: com.feraxhp.gallery.model.GalleryImage,
        val allImages: List<com.feraxhp.gallery.model.GalleryImage>
    ) : Destination
}
