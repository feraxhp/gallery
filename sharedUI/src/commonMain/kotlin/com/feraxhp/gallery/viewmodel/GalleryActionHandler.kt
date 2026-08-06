package com.feraxhp.gallery.viewmodel

interface GalleryActionHandler {
    fun markAsDeleted(imageId: Long)
    fun hideImage(imageId: Long)
    fun restoreImage(imageId: Long)
}
