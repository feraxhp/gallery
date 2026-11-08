package com.feraxhp.gallery.repository

import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface ImageRepository {
    val mediaChanges: Flow<Unit>
        get() = emptyFlow()

    suspend fun getImages(): List<GalleryImage>
    suspend fun getImagesByAlbum(albumId: String): List<GalleryImage>
    suspend fun getAlbums(): List<Album>
    suspend fun getImageById(id: Long, type: com.feraxhp.gallery.model.MediaType): GalleryImage?
    suspend fun getImageByUri(uri: String): GalleryImage?
    suspend fun loadFullMetadata(image: GalleryImage): GalleryImage
    suspend fun deleteImage(image: GalleryImage): Boolean
    suspend fun moveImage(image: GalleryImage, albumId: String): GalleryImage?
    suspend fun copyImage(image: GalleryImage): Boolean
    suspend fun renameImage(image: GalleryImage, newName: String): GalleryImage? = null
    suspend fun createAlbum(name: String): Album?
    fun openInFileManager(path: String)
    fun shareImage(image: GalleryImage)
    fun shareImages(images: List<GalleryImage>)
    fun useImageAs(image: GalleryImage) {}
    suspend fun refreshMedia()
    suspend fun saveEditedImage(
        sourceUri: String,
        totalRotation: Float,
        flipH: Boolean,
        flipV: Boolean,
        straightenAngle: Float,
        normalizedCropRect: androidx.compose.ui.geometry.Rect?,
        boundsMode: com.feraxhp.gallery.viewmodel.BoundsMode = com.feraxhp.gallery.viewmodel.BoundsMode.FIT
    ): GalleryImage? = null
}
