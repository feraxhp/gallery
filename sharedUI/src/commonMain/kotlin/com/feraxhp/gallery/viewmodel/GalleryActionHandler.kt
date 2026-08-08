package com.feraxhp.gallery.viewmodel

import com.feraxhp.gallery.model.GalleryImage
import kotlinx.coroutines.flow.StateFlow

interface GalleryActionHandler {
    val images: StateFlow<List<GalleryImage>>
    val selectedImageIds: StateFlow<Set<Long>>
    val deletedImageIds: StateFlow<Set<Long>>
    fun markAsDeleted(imageIds: Set<Long>)
    fun hideImage(imageId: Long)
    fun restoreImage(imageId: Long)
    fun clearSelection()
    fun clearDeletedState()
}
