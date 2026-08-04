package com.feraxhp.gallery.repository

import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage

interface ImageRepository {
    suspend fun getImages(): List<GalleryImage>
    suspend fun getImagesByAlbum(albumId: String): List<GalleryImage>
    suspend fun getAlbums(): List<Album>
}
