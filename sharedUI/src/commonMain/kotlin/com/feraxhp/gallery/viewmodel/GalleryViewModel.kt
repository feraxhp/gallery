package com.feraxhp.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel(private val repository: ImageRepository) : ViewModel() {

    private val _images = MutableStateFlow<List<GalleryImage>>(emptyList())
    val images: StateFlow<List<GalleryImage>> = _images.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadImages() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _images.value = repository.getImages()
            } catch (e: Exception) {
                // Manejar error si es necesario
            } finally {
                _isLoading.value = false
            }
        }
    }
}
