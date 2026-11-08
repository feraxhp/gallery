package com.feraxhp.gallery.navigation

import com.feraxhp.gallery.model.GalleryImage
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination {
    val contentKey: String

    @Serializable
    data object Permissions : Destination {
        override val contentKey: String get() = "Permissions"
    }
    
    @Serializable
    data object Home : Destination {
        override val contentKey: String get() = "Home"
    }
    
    @Serializable
    data class AlbumGallery(val albumId: String, val albumName: String) : Destination {
        override val contentKey: String get() = "AlbumGallery:$albumId"
    }
    
    @Serializable
    data class ExternalDetail(val uri: String) : Destination {
        override val contentKey: String get() = "ExternalDetail:$uri"
    }
    
    @Serializable
    data class Detail(
        val image: GalleryImage,
        val isExternal: Boolean = false
    ) : Destination {
        override val contentKey: String get() = "Detail:${image.id}:${image.uri}"
    }
    
    @Serializable
    data class MoveToAlbum(
        val imageIds: List<Long>,
        val fallbackImage: GalleryImage? = null
    ) : Destination {
        override val contentKey: String get() = "MoveToAlbum:${imageIds.hashCode()}"
    }
    
    @Serializable
    data class Edit(val uri: String) : Destination {
        override val contentKey: String get() = "Edit:$uri"
    }
}
