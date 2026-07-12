package com.feraxhp.gallery.repository

import com.feraxhp.gallery.model.GalleryImage

interface ImageRepository {
    suspend fun getImages(): List<GalleryImage>
}
