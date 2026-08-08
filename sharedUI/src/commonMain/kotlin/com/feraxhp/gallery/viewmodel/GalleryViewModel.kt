package com.feraxhp.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(private val repository: ImageRepository) : ViewModel(), GalleryActionHandler {

    private val logger = Logger.withTag("GalleryViewModel")
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

    private val _deletedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    override val deletedImageIds: StateFlow<Set<Long>> = _deletedImageIds.asStateFlow()

    override fun markAsDeleted(imageIds: Set<Long>) {
        _deletedImageIds.value = imageIds
    }

    override fun hideImage(imageId: Long) {
        _hiddenImageIds.value += imageId
    }

    override fun restoreImage(imageId: Long) {
        _hiddenImageIds.value -= imageId
    }

    override fun clearDeletedState() {
        _deletedImageIds.value = emptySet()
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

    fun loadImages() {
        logger.d { "loadImages called" }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = repository.getImages()
                logger.d { "Loaded ${result.size} images" }
                _images.value = result
            } catch (e: Exception) {
                logger.e(e) { "Error loading images" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshGallery() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshMedia()
                val result = repository.getImages()
                _images.value = result
            } catch (e: Exception) {
                logger.e(e) { "Error refreshing gallery" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
