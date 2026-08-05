package com.feraxhp.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(private val repository: ImageRepository) : ViewModel() {

    private val logger = Logger.withTag("GalleryViewModel")
    private val _images = MutableStateFlow<List<GalleryImage>>(emptyList())
    val images: StateFlow<List<GalleryImage>> = _images.asStateFlow()

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _deletedImageId = MutableStateFlow<Long?>(null)
    val deletedImageId: StateFlow<Long?> = _deletedImageId.asStateFlow()

    fun markAsDeleted(imageId: Long) {
        _deletedImageId.value = imageId
    }

    fun clearDeletedState() {
        _deletedImageId.value = null
    }

    fun loadImages(albumId: String? = null) {
        logger.d { "loadImages called with albumId: $albumId" }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = if (albumId == null) {
                    repository.getImages()
                } else {
                    repository.getImagesByAlbum(albumId)
                }
                logger.d { "Loaded ${result.size} images" }
                _images.value = result
            } catch (e: Exception) {
                logger.e(e) { "Error loading images" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _albums.value = repository.getAlbums()
            } catch (e: Exception) {
                // Manejar error
            } finally {
                _isLoading.value = false
            }
        }
    }
}
