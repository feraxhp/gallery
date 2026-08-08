package com.feraxhp.gallery.repository

import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage

interface ImageRepository {
    suspend fun getImages(): List<GalleryImage>
    suspend fun getImagesByAlbum(albumId: String): List<GalleryImage>
    suspend fun getAlbums(): List<Album>
    suspend fun getImageById(id: Long, type: com.feraxhp.gallery.model.MediaType): GalleryImage?
    suspend fun deleteImage(image: GalleryImage): Boolean
    suspend fun moveImage(image: GalleryImage, albumId: String): GalleryImage?
    suspend fun copyImage(image: GalleryImage): Boolean
    fun openInFileManager(path: String)
    fun shareImage(image: GalleryImage)
    fun shareImages(images: List<GalleryImage>)
    suspend fun refreshMedia()
}
