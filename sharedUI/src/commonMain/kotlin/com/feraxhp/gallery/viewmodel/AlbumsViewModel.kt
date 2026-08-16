package com.feraxhp.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumsViewModel(private val repository: ImageRepository) : ViewModel() {

    private val logger = Logger.withTag("AlbumsViewModel")
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun loadAlbums() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _albums.value = repository.getAlbums()
            } catch (e: Exception) {
                logger.e(e) { "Error loading albums" }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshAlbums() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshMedia()
                _albums.value = repository.getAlbums()
            } catch (e: Exception) {
                logger.e(e) { "Error refreshing albums" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun createAlbum(name: String, onCreated: (Album) -> Unit) {
        viewModelScope.launch {
            try {
                val album = repository.createAlbum(name)
                if (album != null) {
                    onCreated(album)
                    loadAlbums()
                }
            } catch (e: Exception) {
                logger.e(e) { "Error creating album" }
            }
        }
    }
}
