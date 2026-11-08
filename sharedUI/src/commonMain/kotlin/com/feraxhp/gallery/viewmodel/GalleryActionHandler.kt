package com.feraxhp.gallery.viewmodel

import com.feraxhp.gallery.model.GalleryImage
import kotlinx.coroutines.flow.StateFlow

interface GalleryActionHandler {
    val images: StateFlow<List<GalleryImage>>
    val selectedImageIds: StateFlow<Set<Long>>
    val deletedImageIds: StateFlow<Set<Long>>
    val loadingMetadataIds: StateFlow<Set<Long>>
    val isDeletedFromDetail: StateFlow<Boolean>
    fun markAsDeleted(imageIds: Set<Long>, fromDetail: Boolean = false)
    fun loadFullMetadata(image: GalleryImage)
    fun hideImage(imageId: Long)
    fun restoreImage(imageId: Long)
    fun restoreSelection(imageIds: Set<Long>)
    fun upsertImage(image: GalleryImage)
    suspend fun renameImage(image: GalleryImage, newName: String): GalleryImage?
    fun clearSelection()
    fun clearDeletedState()
    fun loadImages(force: Boolean = false)
}
