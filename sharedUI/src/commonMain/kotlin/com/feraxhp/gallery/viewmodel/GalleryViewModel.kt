package com.feraxhp.gallery.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaFilter
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.model.SortOrder
import com.feraxhp.gallery.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: ImageRepository,
    private val albumId: String? = null,
    initialFilter: MediaFilter = MediaFilter.ALL
) : ViewModel(), GalleryActionHandler {

    private val logger = Logger.withTag("GalleryViewModel")
    private val _images = MutableStateFlow<List<GalleryImage>>(emptyList())
    private val _hiddenImageIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _loadingMetadataIds = MutableStateFlow<Set<Long>>(emptySet())
    override val loadingMetadataIds: StateFlow<Set<Long>> = _loadingMetadataIds.asStateFlow()

    private val _mediaFilter = MutableStateFlow(initialFilter)
    val mediaFilter: StateFlow<MediaFilter> = _mediaFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    override val images: StateFlow<List<GalleryImage>> = combine(
        _images,
        _hiddenImageIds,
        _mediaFilter,
        _sortOrder
    ) { images, hiddenIds, filter, sort ->
        val filtered = images.filter { img ->
            if (img.id in hiddenIds) return@filter false
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.IMAGES -> img.type == MediaType.IMAGE
                MediaFilter.VIDEOS -> img.type == MediaType.VIDEO
                MediaFilter.MOTION_PHOTOS -> img.isMotionPhoto
            }
        }
        when (sort) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateAdded }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.dateAdded }
            SortOrder.SIZE_DESC -> filtered.sortedByDescending { it.size ?: 0L }
            SortOrder.SIZE_ASC -> filtered.sortedBy { it.size ?: 0L }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    override val selectedImageIds: StateFlow<Set<Long>> = _selectedImageIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedImageIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _deletedImageIds = MutableStateFlow<Set<Long>>(emptySet())
    override val deletedImageIds: StateFlow<Set<Long>> = _deletedImageIds.asStateFlow()

    private val _isDeletedFromDetail = MutableStateFlow(false)
    override val isDeletedFromDetail: StateFlow<Boolean> = _isDeletedFromDetail.asStateFlow()

    var savedScrollIndex: Int = 0
    var savedScrollOffset: Int = 0

    init {
        loadImages()
        viewModelScope.launch {
            repository.mediaChanges.collect {
                loadImages()
            }
        }
    }

    fun setMediaFilter(filter: MediaFilter) {
        _mediaFilter.value = filter
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    override fun markAsDeleted(imageIds: Set<Long>, fromDetail: Boolean) {
        _isDeletedFromDetail.value = fromDetail
        _deletedImageIds.value = imageIds
    }

    override fun loadFullMetadata(image: GalleryImage) {
        if (image.id in _loadingMetadataIds.value) return
        
        // Evitar recarga si ya tenemos metadatos básicos de cámara o ubicación
        if (image.cameraModel != null || image.latitude != null || image.shutterSpeed != null) return

        viewModelScope.launch {
            _loadingMetadataIds.value += image.id
            try {
                val updatedImage = repository.loadFullMetadata(image)
                _images.value = _images.value.map {
                    if (it.id == image.id) updatedImage else it
                }
            } catch (e: Exception) {
                logger.e(e) { "Error loading full metadata for image ${image.id}" }
            } finally {
                _loadingMetadataIds.value -= image.id
            }
        }
    }

    override fun hideImage(imageId: Long) {
        _hiddenImageIds.value += imageId
    }

    override fun restoreImage(imageId: Long) {
        _hiddenImageIds.value -= imageId
    }

    override fun restoreSelection(imageIds: Set<Long>) {
        if (_selectedImageIds.value.isEmpty() && imageIds.isNotEmpty()) {
            _selectedImageIds.value = imageIds
        }
    }

    override fun upsertImage(image: GalleryImage) {
        val current = _images.value
        val existingIdx = current.indexOfFirst { it.id == image.id }
        _images.value = if (existingIdx >= 0) {
            current.toMutableList().apply { set(existingIdx, image) }
        } else {
            (listOf(image) + current).sortedByDescending { it.dateAdded }
        }
    }

    override suspend fun renameImage(image: GalleryImage, newName: String): GalleryImage? {
        val updated = repository.renameImage(image, newName) ?: return null
        val current = _images.value.toMutableList()
        val idx = current.indexOfFirst { it.id == image.id || it.id == updated.id }
        if (idx >= 0) {
            current[idx] = updated
            _images.value = current
        } else {
            upsertImage(updated)
        }
        return updated
    }

    override fun clearDeletedState() {
        _deletedImageIds.value = emptySet()
        _isDeletedFromDetail.value = false
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

    override fun loadImages(force: Boolean) {
        logger.d { "loadImages called for albumId: $albumId (force=$force)" }
        viewModelScope.launch {
            val wasEmpty = _images.value.isEmpty()
            if (wasEmpty) {
                _isLoading.value = true
            }
            try {
                val existingById = _images.value.associateBy { it.id }
                val result = if (albumId != null) {
                    repository.getImagesByAlbum(albumId)
                } else {
                    repository.getImages()
                }
                val merged = result.map { item ->
                    val prev = existingById[item.id]
                    if (prev != null && (prev.cameraModel != null || prev.latitude != null || prev.shutterSpeed != null)) {
                        item.copy(
                            latitude = prev.latitude,
                            longitude = prev.longitude,
                            shutterSpeed = prev.shutterSpeed,
                            cameraModel = prev.cameraModel,
                            cameraManufacturer = prev.cameraManufacturer,
                            iso = prev.iso,
                            aperture = prev.aperture,
                            focalLength = prev.focalLength,
                            software = prev.software
                        )
                    } else {
                        item
                    }
                }
                logger.d { "Loaded ${merged.size} images" }
                _images.value = merged
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
                loadImages(force = true)
            } catch (e: Exception) {
                logger.e(e) { "Error refreshing gallery" }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
