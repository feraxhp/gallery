package com.feraxhp.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.repository.ImageRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AlbumGalleryViewModel(private val repository: ImageRepository) : ViewModel(), GalleryActionHandler {

    private val logger = Logger.withTag("AlbumGalleryViewModel")
    
    private val _images = MutableStateFlow<List<GalleryImage>>(emptyList())
    private val _hiddenImageIds = MutableStateFlow<Set<Long>>(emptySet())
    
    override val images: StateFlow<List<GalleryImage>> = combine(_images, _hiddenImageIds) { images, hiddenIds ->
        images.filter { it.id !in hiddenIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    override val selectedImageIds: StateFlow<Set<Long>> = _selectedImageIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedImageIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _deletedImageId = MutableStateFlow<Long?>(null)
    val deletedImageId: StateFlow<Long?> = _deletedImageId.asStateFlow()

    override fun markAsDeleted(imageId: Long) {
        _deletedImageId.value = imageId
    }

    override fun hideImage(imageId: Long) {
        _hiddenImageIds.value += imageId
    }

    override fun restoreImage(imageId: Long) {
        _hiddenImageIds.value -= imageId
    }

    fun clearDeletedState() {
        _deletedImageId.value = null
    }

    fun toggleSelection(imageId: Long) {
        if (imageId in _selectedImageIds.value) {
            _selectedImageIds.value -= imageId
        } else {
            _selectedImageIds.value += imageId
        }
    }

    fun setSelection(imageId: Long, selected: Boolean) {
        if (selected) {
            _selectedImageIds.value += imageId
        } else {
            _selectedImageIds.value -= imageId
        }
    }

    override fun clearSelection() {
        _selectedImageIds.value = emptySet()
    }

    fun loadImages(albumId: String) {
        logger.d { "loadImages called with albumId: $albumId" }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getImagesByAlbum(albumId)
                logger.d { "Loaded ${result.size} images for album $albumId" }
                _images.value = result
            } catch (e: Exception) {
                logger.e(e) { "Error loading album images" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshGallery(albumId: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshMedia()
                val result = repository.getImagesByAlbum(albumId)
                _images.value = result
            } catch (e: Exception) {
                logger.e(e) { "Error refreshing album gallery" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
