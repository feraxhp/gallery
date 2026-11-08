// Copyright (C) 2026 feraxhp
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
package com.feraxhp.gallery

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaFilter
import com.feraxhp.gallery.model.PickerConfig
import com.feraxhp.gallery.navigation.Destination
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.screens.AlbumGalleryScreen
import com.feraxhp.gallery.screens.DetailScreen
import com.feraxhp.gallery.screens.EditScreen
import com.feraxhp.gallery.screens.HomeScreen
import com.feraxhp.gallery.screens.MoveToAlbumScreen
import com.feraxhp.gallery.screens.PermissionsScreen
import com.feraxhp.gallery.util.SetSystemBarsColor
import com.feraxhp.gallery.viewmodel.GalleryActionHandler
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import com.feraxhp.ktheme.DynamicTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

private val appStateJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val backStackSaver = Saver<List<Destination>, String>(
    save = { list ->
        runCatching { appStateJson.encodeToString(list) }.getOrDefault("")
    },
    restore = { str ->
        if (str.isBlank()) {
            listOf(Destination.Home)
        } else {
            runCatching { appStateJson.decodeFromString<List<Destination>>(str) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?: listOf(Destination.Home)
        }
    }
)

private val nullableGalleryImageSaver = Saver<GalleryImage?, String>(
    save = { img ->
        img?.let { runCatching { appStateJson.encodeToString(it) }.getOrNull() } ?: ""
    },
    restore = { str ->
        if (str.isBlank()) null else runCatching { appStateJson.decodeFromString<GalleryImage>(str) }.getOrNull()
    }
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun App(
    repository: ImageRepository,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    initialMediaUri: String? = null,
    onUriConsumed: () -> Unit = {},
    pickerConfig: PickerConfig? = null,
    onPickResult: (List<GalleryImage>) -> Unit = {},
    onCancelPicker: () -> Unit = {},
    onRequestReadPermission: () -> Unit,
    onRequestWritePermission: () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    var backStack by rememberSaveable(stateSaver = backStackSaver) {
        mutableStateOf(
            listOf<Destination>(
                if (hasReadPermission && hasWritePermission) Destination.Home else Destination.Permissions
            )
        )
    }

    LaunchedEffect(initialMediaUri, hasReadPermission, hasWritePermission) {
        if (initialMediaUri != null && hasReadPermission && hasWritePermission) {
            backStack = listOf(Destination.Home, Destination.ExternalDetail(initialMediaUri))
            onUriConsumed()
        }
    }

    val currentReadPermission by rememberUpdatedState(hasReadPermission)
    val currentWritePermission by rememberUpdatedState(hasWritePermission)

    val homeViewModel: GalleryViewModel = viewModel(key = "home_gallery") {
        GalleryViewModel(
            repository = repository,
            initialFilter = pickerConfig?.initialFilter ?: MediaFilter.ALL
        )
    }

    LaunchedEffect(pickerConfig) {
        if (pickerConfig != null) {
            homeViewModel.setMediaFilter(pickerConfig.initialFilter)
        }
    }

    val activeAlbumDest = backStack.lastOrNull { it is Destination.AlbumGallery } as? Destination.AlbumGallery
    val albumViewModel: GalleryViewModel? = if (activeAlbumDest != null) {
        viewModel(key = "album_${activeAlbumDest.albumId}") {
            GalleryViewModel(
                repository = repository,
                albumId = activeAlbumDest.albumId,
                initialFilter = pickerConfig?.initialFilter ?: MediaFilter.ALL
            )
        }
    } else {
        null
    }
    val activeActionHandler: GalleryActionHandler = albumViewModel ?: homeViewModel

    var savedSelectedIds by rememberSaveable { mutableStateOf(LongArray(0)) }

    LaunchedEffect(activeActionHandler) {
        if (savedSelectedIds.isNotEmpty() && activeActionHandler.selectedImageIds.value.isEmpty()) {
            activeActionHandler.restoreSelection(savedSelectedIds.toSet())
        }
    }

    LaunchedEffect(hasReadPermission, hasWritePermission) {
        if (hasReadPermission && hasWritePermission && backStack.lastOrNull() == Destination.Permissions) {
            backStack = listOf(Destination.Home)
        }
    }

    val currentDestination = backStack.lastOrNull()
    var isDetailActive by rememberSaveable { mutableStateOf(false) }
    var isProcessingAction by remember { mutableStateOf(false) }
    var actionProgress by remember { mutableStateOf(0f) }
    var actionProgressText by remember { mutableStateOf("") }
    var currentImageInDetail by rememberSaveable(stateSaver = nullableGalleryImageSaver) {
        mutableStateOf<GalleryImage?>(null)
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteJob by remember { mutableStateOf<Job?>(null) }
    var activePendingDeleteData by remember { mutableStateOf<Pair<List<GalleryImage>, suspend () -> Unit>?>(null) }
    val scope = rememberCoroutineScope()

    val selectedIds by activeActionHandler.selectedImageIds.collectAsState()
    val allImages by activeActionHandler.images.collectAsState()

    LaunchedEffect(selectedIds) {
        savedSelectedIds = selectedIds.toLongArray()
    }

    val selectedImages = remember(selectedIds, allImages) {
        allImages.filter { it.id in selectedIds }
    }

    SetSystemBarsColor(isDark = isDetailActive || isSystemDark)

    if (showDeleteConfirmation && (currentImageInDetail != null || selectedImages.isNotEmpty())) {
        val imagesToDelete = if (isDetailActive) listOfNotNull(currentImageInDetail) else selectedImages
        val title = if (imagesToDelete.size == 1) "Borrar imagen" else "Borrar ${imagesToDelete.size} imágenes"
        val message = if (imagesToDelete.size == 1) "¿Estás seguro de que quieres borrar esta imagen?" else "¿Estás seguro de que quieres borrar estas ${imagesToDelete.size} imágenes?"
        
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    val currentBackstack = backStack
                    showDeleteConfirmation = false
                    
                    val prevPending = activePendingDeleteData
                    if (prevPending != null) {
                        scope.launch {
                            withContext(NonCancellable) {
                                prevPending.second()
                            }
                        }
                        activePendingDeleteData = null
                    }

                    snackbarHostState.currentSnackbarData?.dismiss()
                    pendingDeleteJob?.cancel()

                    val isFromDetail = isDetailActive
                    val vmImages = activeActionHandler.images.value
                    val detailDest = currentDestination as? Destination.Detail
                    val imagesInDetail = if (detailDest?.isExternal == true) {
                        listOfNotNull(currentImageInDetail ?: detailDest.image)
                    } else {
                        vmImages
                    }

                    var shouldExitDetail = false
                    var targetImageAfterDelete: GalleryImage? = null

                    if (isFromDetail) {
                        val totalDetailCount = imagesInDetail.size
                        if (totalDetailCount <= 1) {
                            shouldExitDetail = true
                            if (backStack.size > 1) {
                                backStack = backStack.dropLast(1)
                            }
                        } else {
                            shouldExitDetail = false
                            val currentIndex = imagesInDetail.indexOfFirst { it.id == currentImageInDetail?.id }
                            if (currentIndex >= 0) {
                                val nextIndex = if (currentIndex == totalDetailCount - 1) {
                                    currentIndex - 1
                                } else {
                                    currentIndex + 1
                                }
                                targetImageAfterDelete = imagesInDetail.getOrNull(nextIndex)
                            }
                        }
                    }

                    val commitAction: suspend () -> Unit = {
                        imagesToDelete.forEach { repository.deleteImage(it) }
                    }
                    activePendingDeleteData = Pair(imagesToDelete, commitAction)

                    pendingDeleteJob = scope.launch {
                        val handler = activeActionHandler
                        
                        handler.markAsDeleted(imagesToDelete.map { it.id }.toSet(), fromDetail = isFromDetail)
                        
                        if (isFromDetail && !shouldExitDetail && targetImageAfterDelete != null) {
                            currentImageInDetail = targetImageAfterDelete
                            delay(400.milliseconds)
                        } else {
                            val deleteDelay = if (isFromDetail && shouldExitDetail) 1000.milliseconds else 600.milliseconds
                            delay(deleteDelay)
                        }
                        
                        imagesToDelete.forEach { handler.hideImage(it.id) }
                        handler.clearDeletedState()
                        handler.clearSelection()
                        
                        val snackbarMessage = if (imagesToDelete.size == 1) "Imagen eliminada" else "${imagesToDelete.size} imágenes eliminadas"
                        val result = snackbarHostState.showSnackbar(
                            message = snackbarMessage,
                            actionLabel = "Deshacer",
                            duration = SnackbarDuration.Short
                        )
                        
                        if (result == SnackbarResult.ActionPerformed) {
                            imagesToDelete.forEach { handler.restoreImage(it.id) }
                            handler.clearDeletedState()
                            if (isFromDetail) {
                                if (shouldExitDetail) {
                                    backStack = currentBackstack
                                } else {
                                    imagesToDelete.firstOrNull()?.let { restored ->
                                        scope.launch {
                                            delay(150.milliseconds)
                                            currentImageInDetail = restored
                                        }
                                    }
                                }
                            }
                            activePendingDeleteData = null
                        } else {
                            withContext(NonCancellable) {
                                commitAction()
                            }
                            activePendingDeleteData = null
                        }
                    }
                }) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    DynamicTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        ) {
            SharedTransitionLayout {
                NavDisplay(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    backStack = backStack,
                    sharedTransitionScope = this,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack = backStack.dropLast(1)
                        }
                    },
                    popTransitionSpec = {
                        val duration = 600
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    predictivePopTransitionSpec = {
                        val initial = initialState.key
                        val target = targetState.key
                        
                        val isAlbumGalleryToHome = initial is Destination.AlbumGallery && target is Destination.Home

                        val scale = if (isAlbumGalleryToHome) 0.8f else 1.0f
                        val targetAlpha = if (isAlbumGalleryToHome) 0.8f else 0.0f
                        val duration = 5

                        fadeIn(tween(duration)) togetherWith
                                fadeOut(tween(duration), targetAlpha = targetAlpha) +
                                scaleOut(targetScale = scale, animationSpec = tween(duration))
                    },
                    transitionSpec = {
                        val duration = 600
                        fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                    },
                    entryProvider = { key: Destination ->
                        when (key) {
                            Destination.Permissions -> NavEntry(key, contentKey = key.contentKey) {
                                PermissionsScreen(
                                    hasReadPermission = currentReadPermission,
                                    hasWritePermission = currentWritePermission,
                                    onRequestReadPermission = onRequestReadPermission,
                                    onRequestWritePermission = onRequestWritePermission,
                                    topPadding = 0.dp,
                                    onContinue = {
                                        backStack = listOf(Destination.Home)
                                    }
                                )
                            }

                            is Destination.ExternalDetail -> NavEntry(key, contentKey = key.contentKey) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                                LaunchedEffect(key.uri) {
                                    val image = repository.getImageByUri(key.uri)
                                    if (image != null) {
                                        currentImageInDetail = image
                                        backStack = backStack.dropLast(1) + Destination.Detail(image, isExternal = true)
                                    } else {
                                        backStack = listOf(Destination.Home)
                                    }
                                }
                            }

                            Destination.Home -> NavEntry(key, contentKey = key.contentKey) {
                                LaunchedEffect(Unit) { isDetailActive = false }
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current

                                HomeScreen(
                                    repository = repository,
                                    galleryViewModel = homeViewModel,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onNavigateToDetail = { image, _ ->
                                        currentImageInDetail = image
                                        backStack = backStack + Destination.Detail(image, isExternal = false)
                                    },
                                    onNavigateToAlbumGallery = { album ->
                                        backStack = backStack + Destination.AlbumGallery(album.id, album.name)
                                    },
                                    onNavigateToMoveToAlbum = { images, _ ->
                                        backStack = backStack + Destination.MoveToAlbum(
                                            imageIds = images.map { it.id },
                                            fallbackImage = images.firstOrNull()
                                        )
                                    },
                                    onShareImages = { images ->
                                        repository.shareImages(images)
                                    },
                                    onDeleteImages = {
                                        showDeleteConfirmation = true
                                    },
                                    pickerConfig = pickerConfig,
                                    onPickImages = onPickResult,
                                    onCancelPicker = onCancelPicker
                                )
                            }

                            is Destination.AlbumGallery -> NavEntry(key, contentKey = key.contentKey) {
                                LaunchedEffect(Unit) { isDetailActive = false }
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                val viewModel: GalleryViewModel = viewModel(key = "album_${key.albumId}") {
                                    GalleryViewModel(
                                        repository = repository,
                                        albumId = key.albumId,
                                        initialFilter = pickerConfig?.initialFilter ?: MediaFilter.ALL
                                    )
                                }
                                AlbumGalleryScreen(
                                    viewModel = viewModel,
                                    albumId = key.albumId,
                                    albumName = key.albumName,
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                    },
                                    onImageClick = { image, _ ->
                                        currentImageInDetail = image
                                        backStack = backStack + Destination.Detail(image, isExternal = false)
                                    },
                                    onNavigateToMoveToAlbum = { images, _ ->
                                        backStack = backStack + Destination.MoveToAlbum(
                                            imageIds = images.map { it.id },
                                            fallbackImage = images.firstOrNull()
                                        )
                                    },
                                    onShareImages = { images ->
                                        repository.shareImages(images)
                                    },
                                    onDeleteImages = { _ ->
                                        showDeleteConfirmation = true
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    pickerConfig = pickerConfig,
                                    onPickImages = onPickResult
                                )
                            }

                            is Destination.Detail -> NavEntry(
                                key,
                                contentKey = key.contentKey,
                                metadata = metadata {
                                    put(NavDisplay.TransitionKey) {
                                        fadeIn(tween(400)) togetherWith fadeOut(tween(400)) + ExitTransition.KeepUntilTransitionsFinished
                                    }
                                    put(NavDisplay.PopTransitionKey) {
                                        fadeIn(tween(400)) togetherWith fadeOut(tween(400)) + ExitTransition.KeepUntilTransitionsFinished
                                    }
                                }
                            ) {
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                val isTargetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible
                                LaunchedEffect(isTargetVisible) {
                                    isDetailActive = isTargetVisible
                                }

                                val vmImages by activeActionHandler.images.collectAsState()
                                val activeDetailImage = currentImageInDetail ?: key.image
                                val imagesToShow = remember(key.isExternal, key.image, activeDetailImage, vmImages) {
                                    if (key.isExternal) {
                                        listOf(activeDetailImage)
                                    } else if (vmImages.isNotEmpty()) {
                                        vmImages
                                    } else {
                                        listOf(activeDetailImage)
                                    }
                                }
                                val loadingIds by activeActionHandler.loadingMetadataIds.collectAsState()
                                val initialIdx = remember(imagesToShow, activeDetailImage.id, key.image.id) {
                                    imagesToShow.indexOfFirst { it.id == activeDetailImage.id }
                                        .takeIf { it >= 0 }
                                        ?: imagesToShow.indexOfFirst { it.id == key.image.id }.coerceAtLeast(0)
                                }

                                DetailScreen(
                                    images = imagesToShow,
                                    initialIndex = initialIdx,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    repository = repository,
                                    onImageChange = { currentImageInDetail = it },
                                    onLoadMetadata = { activeActionHandler.loadFullMetadata(it) },
                                    isMetadataLoading = activeDetailImage.id in loadingIds,
                                    selectedImageId = activeDetailImage.id,
                                    topPadding = 0.dp,
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                    },
                                    onEdit = {
                                        val img = currentImageInDetail ?: key.image
                                        backStack = backStack + Destination.Edit(img.uri)
                                    },
                                    onShare = {
                                        val img = currentImageInDetail ?: key.image
                                        repository.shareImage(img)
                                    },
                                    onUseAs = {
                                        val img = currentImageInDetail ?: key.image
                                        repository.useImageAs(img)
                                    },
                                    onMoveToAlbum = {
                                        val img = currentImageInDetail ?: key.image
                                        backStack = backStack + Destination.MoveToAlbum(
                                            imageIds = listOf(img.id),
                                            fallbackImage = img
                                        )
                                    },
                                    onDelete = {
                                        showDeleteConfirmation = true
                                    },
                                    onRenameImage = { image, newName ->
                                        val updated = activeActionHandler.renameImage(image, newName)
                                        if (updated != null) {
                                            currentImageInDetail = updated
                                            homeViewModel.upsertImage(updated)
                                            albumViewModel?.upsertImage(updated)
                                            if (backStack.lastOrNull() is Destination.Detail) {
                                                val detailDest = backStack.last() as Destination.Detail
                                                backStack = backStack.dropLast(1) + detailDest.copy(image = updated)
                                            }
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar("Renombrado a ${updated.name}")
                                            }
                                        } else {
                                            scope.launch {
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar("Error al renombrar")
                                            }
                                        }
                                        updated
                                    },
                                    onPickConfirm = if (pickerConfig != null) {
                                        { pickedImage -> onPickResult(listOf(pickedImage)) }
                                    } else null
                                )
                            }

                            is Destination.Edit -> NavEntry(key, contentKey = key.contentKey) {
                                EditScreen(
                                    imageUri = key.uri,
                                    repository = repository,
                                    onImageSaved = { savedImage ->
                                        currentImageInDetail = savedImage
                                        homeViewModel.upsertImage(savedImage)
                                        albumViewModel?.upsertImage(savedImage)
                                        if (backStack.size > 1 && backStack[backStack.size - 2] is Destination.Detail) {
                                            val detailDest = backStack[backStack.size - 2] as Destination.Detail
                                            backStack = backStack.dropLast(2) + detailDest.copy(image = savedImage)
                                        } else if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            snackbarHostState.showSnackbar("Imagen guardada")
                                        }
                                    },
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                    }
                                )
                            }

                            is Destination.MoveToAlbum -> NavEntry(key, contentKey = key.contentKey) {
                                MoveToAlbumScreen(
                                    repository = repository,
                                    isProcessing = isProcessingAction,
                                    progress = actionProgress,
                                    progressText = actionProgressText,
                                    onAlbumSelected = { album ->
                                        scope.launch {
                                            isProcessingAction = true
                                            val handler = activeActionHandler
                                            val vmImages = handler.images.value
                                            val imagesToMove = vmImages.filter { it.id in key.imageIds }
                                                .ifEmpty { listOfNotNull(key.fallbackImage) }
                                            val total = imagesToMove.size.coerceAtLeast(1)
                                            val originalImagesState = imagesToMove.map { it.id to it.albumId }
                                            
                                            val updatedImages = mutableListOf<GalleryImage>()
                                            val successfulMoves = mutableListOf<Pair<GalleryImage, GalleryImage?>>()
                                            
                                            imagesToMove.forEachIndexed { index, image ->
                                                actionProgress = (index + 1).toFloat() / total
                                                actionProgressText = "${index + 1} de $total"
                                                
                                                val movedImage = repository.moveImage(image, album.id)
                                                successfulMoves.add(image to movedImage)
                                                updatedImages.add(movedImage ?: image)
                                            }
                                            
                                            val anySuccess = successfulMoves.any { it.second != null }
                                            
                                            if (anySuccess) {
                                                homeViewModel.loadImages(force = true)
                                                albumViewModel?.loadImages(force = true)

                                                if (backStack.size > 1 && backStack[backStack.size - 2] is Destination.Detail) {
                                                    val detailDest = backStack[backStack.size - 2] as Destination.Detail
                                                    val newCurrentImage = updatedImages.find { it.id == (currentImageInDetail?.id ?: detailDest.image.id) }
                                                        ?: updatedImages.firstOrNull()
                                                        ?: detailDest.image
                                                    currentImageInDetail = newCurrentImage
                                                    backStack = backStack.dropLast(2) + detailDest.copy(image = newCurrentImage)
                                                } else {
                                                    backStack = backStack.dropLast(1)
                                                }
                                                
                                                handler.clearSelection()
                                                
                                                isProcessingAction = false
                                                
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                val successCount = successfulMoves.count { it.second != null }
                                                val snackbarMessage = if (successCount == 1) 
                                                    "Imagen movida a ${album.name}" 
                                                else "$successCount imágenes movidas a ${album.name}"
                                                
                                                val result = snackbarHostState.showSnackbar(
                                                    message = snackbarMessage,
                                                    actionLabel = "Deshacer",
                                                    duration = SnackbarDuration.Short
                                                )
                                                
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    successfulMoves.filter { it.second != null }.forEach { (originalImage, movedImage) ->
                                                        val originalAlbumId = originalImagesState.find { it.first == originalImage.id }?.second
                                                        if (originalAlbumId != null && movedImage != null) {
                                                            repository.moveImage(movedImage, originalAlbumId)
                                                        }
                                                    }
                                                    homeViewModel.loadImages(force = true)
                                                    albumViewModel?.loadImages(force = true)
                                                    snackbarHostState.showSnackbar("Movimiento cancelado")
                                                }
                                            } else {
                                                isProcessingAction = false
                                                snackbarHostState.currentSnackbarData?.dismiss()
                                                snackbarHostState.showSnackbar("Error al mover")
                                                if (backStack.size > 1) {
                                                    backStack = backStack.dropLast(1)
                                                }
                                            }
                                        }
                                    },
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
            }
            
            // Render Snackbar at the bottom
            Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)) {
                SnackbarHost(snackbarHostState) { data ->
                    val message = data.visuals.message
                    val annotatedString = if (message.startsWith("Imagen movida a ")) {
                        val albumName = message.substringAfter("Imagen movida a ")
                        buildAnnotatedString {
                            append("Imagen movida a ")
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    fontStyle = FontStyle.Italic,
                                    textDecoration = TextDecoration.Underline,
                                )
                            ) {
                                append(albumName)
                            }
                        }
                    } else {
                        AnnotatedString(message)
                    }
                    Snackbar(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .clip(shape = MaterialTheme.shapes.medium),
                        action = {
                            data.visuals.actionLabel?.let { actionLabel ->
                                TextButton(
                                    colors = ButtonDefaults.textButtonColors().copy(
                                        contentColor = MaterialTheme.colorScheme.inversePrimary
                                    ),
                                    onClick = { data.performAction() }
                                ) {
                                    Text(actionLabel)
                                }
                            }
                        }
                    ) {
                        Text(annotatedString)
                    }
                }
            }
        }
    }
}
