package com.feraxhp.gallery.navigation

import com.feraxhp.gallery.model.GalleryImage
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination {
    @Serializable
    data object Permissions : Destination
    
    @Serializable
    data object Home : Destination
    
    @Serializable
    data class AlbumGallery(val albumId: String, val albumName: String) : Destination
    
    @Serializable
    data class ExternalDetail(val uri: String) : Destination
    
    @Serializable
    data class Detail(
        val image: GalleryImage,
        val allImages: List<GalleryImage>
    ) : Destination
    
    @Serializable
    data class MoveToAlbum(
        val images: List<GalleryImage>,
        val allImages: List<GalleryImage>
    ) : Destination
    
    @Serializable
    data class Edit(val uri: String) : Destination
}
