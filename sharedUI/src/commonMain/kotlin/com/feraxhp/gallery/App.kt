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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.components.NavigationItem
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.navigation.Destination
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.screens.AlbumsScreen
import com.feraxhp.gallery.screens.AlbumGalleryScreen
import com.feraxhp.gallery.screens.DetailScreen
import com.feraxhp.gallery.screens.EditScreen
import com.feraxhp.gallery.screens.GalleryScreen
import com.feraxhp.gallery.screens.HomeScreen
import com.feraxhp.gallery.screens.MoveToAlbumScreen
import com.feraxhp.gallery.screens.PermissionsScreen
import com.feraxhp.gallery.util.SetSystemBarsColor
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import com.feraxhp.gallery.viewmodel.GalleryActionHandler
import com.feraxhp.ktheme.DynamicTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun App(
    repository: ImageRepository,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    initialMediaUri: String? = null,
    onUriConsumed: () -> Unit = {},
    onRequestReadPermission: () -> Unit,
    onRequestWritePermission: () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    var backStack by remember {
        mutableStateOf(
            listOf<Destination>(
                if (hasReadPermission && hasWritePermission) Destination.Home else Destination.Permissions
            )
        )
    }

    LaunchedEffect(initialMediaUri, hasReadPermission, hasWritePermission) {
        if (initialMediaUri != null && hasReadPermission && hasWritePermission) {
            // Navegamos al detalle externo instantáneamente
            backStack = listOf(Destination.Home, Destination.ExternalDetail(initialMediaUri))
            onUriConsumed()
        }
    }

    val currentReadPermission by rememberUpdatedState(hasReadPermission)
    val currentWritePermission by rememberUpdatedState(hasWritePermission)

    var activeActionHandler by remember { mutableStateOf<GalleryActionHandler?>(null) }

    LaunchedEffect(hasReadPermission, hasWritePermission) {
        if (hasReadPermission && hasWritePermission && backStack.lastOrNull() == Destination.Permissions) {
            backStack = listOf(Destination.Home)
        }
    }

    val currentDestination = backStack.lastOrNull()
    var isDetailActive by remember { mutableStateOf(false) }
    var isProcessingAction by remember { mutableStateOf(false) }
    var actionProgress by remember { mutableStateOf(0f) }
    var actionProgressText by remember { mutableStateOf("") }
    var currentImageInDetail by remember {
        mutableStateOf<com.feraxhp.gallery.model.GalleryImage?>(
            null
        )
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDeleteJob by remember { mutableStateOf<Job?>(null) }
    var activePendingDeleteData by remember { mutableStateOf<Pair<List<GalleryImage>, suspend () -> Unit>?>(null) }
    val scope = rememberCoroutineScope()

    val selectedIds = activeActionHandler?.selectedImageIds?.collectAsState()?.value ?: emptySet()
    val allImages = activeActionHandler?.images?.collectAsState()?.value ?: emptyList()
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
                    
                    // Si ya había un borrado pendiente sin confirmar/cancelar, concretarlo en disco inmediatamente
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
                    val vmImages = activeActionHandler?.images?.value ?: emptyList()
                    val imagesInDetail = if (vmImages.any { it.id == currentImageInDetail?.id }) vmImages else ((currentDestination as? Destination.Detail)?.allImages ?: emptyList())

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
                        
                        // Activamos la animación de shatter para todas
                        handler?.markAsDeleted(imagesToDelete.map { it.id }.toSet(), fromDetail = isFromDetail)
                        
                        if (isFromDetail && !shouldExitDetail && targetImageAfterDelete != null) {
                            currentImageInDetail = targetImageAfterDelete
                            delay(400.milliseconds)
                        } else {
                            val deleteDelay = if (isFromDetail && shouldExitDetail) 1000.milliseconds else 600.milliseconds
                            delay(deleteDelay)
                        }
                        
                        imagesToDelete.forEach { handler?.hideImage(it.id) }
                        handler?.clearDeletedState()
                        handler?.clearSelection()
                        
                        val snackbarMessage = if (imagesToDelete.size == 1) "Imagen eliminada" else "${imagesToDelete.size} imágenes eliminadas"
                        val result = snackbarHostState.showSnackbar(
                            message = snackbarMessage,
                            actionLabel = "Deshacer",
                            duration = SnackbarDuration.Short
                        )
                        
                        if (result == SnackbarResult.ActionPerformed) {
                            // Si se pulsa Undo, volvemos a mostrarlas
                            imagesToDelete.forEach { handler?.restoreImage(it.id) }
                            handler?.clearDeletedState()
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
                            // Si no se pulsa, borramos definitivamente
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
                            Destination.Permissions -> NavEntry(key) {
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

                            is Destination.ExternalDetail -> NavEntry(key) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                                LaunchedEffect(key.uri) {
                                    val image = repository.getImageByUri(key.uri)
                                    if (image != null) {
                                        backStack = backStack.dropLast(1) + Destination.Detail(image, listOf(image))
                                    } else {
                                        backStack = listOf(Destination.Home)
                                    }
                                }
                            }

                            Destination.Home -> NavEntry(key) {
                                LaunchedEffect(Unit) { isDetailActive = false }
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                val viewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
                                LaunchedEffect(viewModel) { activeActionHandler = viewModel }
                                
                                HomeScreen(
                                    repository = repository,
                                    galleryViewModel = viewModel,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onNavigateToDetail = { image, images ->
                                        currentImageInDetail = image
                                        backStack = backStack + Destination.Detail(image, images)
                                    },
                                    onNavigateToAlbumGallery = { album ->
                                        backStack = backStack + Destination.AlbumGallery(album.id, album.name)
                                    },
                                    onNavigateToMoveToAlbum = { images, all ->
                                        backStack = backStack + Destination.MoveToAlbum(images, all)
                                    },
                                    onShareImages = { images ->
                                        repository.shareImages(images)
                                    },
                                    onDeleteImages = {
                                        showDeleteConfirmation = true
                                    }
                                )
                            }

                            is Destination.AlbumGallery -> NavEntry(key) {
                                LaunchedEffect(Unit) { isDetailActive = false }
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                val viewModel: GalleryViewModel = viewModel(key = key.albumId) { GalleryViewModel(repository, key.albumId) }
                                LaunchedEffect(viewModel) { activeActionHandler = viewModel }
                                AlbumGalleryScreen(
                                    viewModel = viewModel,
                                    albumId = key.albumId,
                                    albumName = key.albumName,
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                    },
                                    onImageClick = { image, allImages ->
                                        currentImageInDetail = image
                                        backStack = backStack + Destination.Detail(image, allImages)
                                    },
                                    onNavigateToMoveToAlbum = { images, all ->
                                        backStack = backStack + Destination.MoveToAlbum(images, all)
                                    },
                                    onShareImages = { images ->
                                        repository.shareImages(images)
                                    },
                                    onDeleteImages = { images ->
                                        showDeleteConfirmation = true
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }

                            is Destination.Detail -> NavEntry(
                                key,
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

                                val vmImages by activeActionHandler?.images?.collectAsState() ?: remember { mutableStateOf(emptyList<GalleryImage>()) }
                                val isExternal = remember(key.image) {
                                    key.allImages.size == 1 && activeActionHandler?.images?.value?.none { it.id == key.image.id } == true
                                }
                                val imagesToShow = if (isExternal) key.allImages else vmImages
                                val loadingIds by activeActionHandler?.loadingMetadataIds?.collectAsState() ?: remember { mutableStateOf(emptySet<Long>()) }

                                DetailScreen(
                                    images = imagesToShow,
                                    initialIndex = imagesToShow.indexOfFirst { it.id == key.image.id }.coerceAtLeast(0),
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    repository = repository,
                                    onImageChange = { currentImageInDetail = it },
                                    onLoadMetadata = { activeActionHandler?.loadFullMetadata(it) },
                                    isMetadataLoading = currentImageInDetail?.id in loadingIds,
                                    selectedImageId = currentImageInDetail?.id,
                                    topPadding = 0.dp,
                                    onBack = {
                                        if (backStack.size > 1) {
                                            backStack = backStack.dropLast(1)
                                        }
                                    },
                                    onEdit = {
                                        currentImageInDetail?.let {
                                            backStack = backStack + Destination.Edit(it.uri)
                                        }
                                    },
                                    onShare = {
                                        currentImageInDetail?.let { repository.shareImage(it) }
                                    },
                                    onMoveToAlbum = {
                                        currentImageInDetail?.let {
                                            backStack = backStack + Destination.MoveToAlbum(listOf(it), imagesToShow)
                                        }
                                    },
                                    onDelete = {
                                        showDeleteConfirmation = true
                                    }
                                )
                            }

                            is Destination.Edit -> NavEntry(key) {
                                EditScreen(
                                    imageUri = key.uri,
                                    repository = repository,
                                    onImageSaved = { savedImage ->
                                        currentImageInDetail = savedImage
                                        if (backStack.size > 1 && backStack[backStack.size - 2] is Destination.Detail) {
                                            val detailDest = backStack[backStack.size - 2] as Destination.Detail
                                            val updatedList = listOf(savedImage) + detailDest.allImages.filter { it.id != savedImage.id }
                                            backStack = backStack.dropLast(2) + Destination.Detail(savedImage, updatedList)
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

                            is Destination.MoveToAlbum -> NavEntry(key) {
                                MoveToAlbumScreen(
                                    repository = repository,
                                    isProcessing = isProcessingAction,
                                    progress = actionProgress,
                                    progressText = actionProgressText,
                                    onAlbumSelected = { album ->
                                        scope.launch {
                                            isProcessingAction = true
                                            val total = key.images.size
                                            val handler = activeActionHandler
                                            val originalImagesState = key.images.map { it.id to it.albumId }
                                            
                                            val updatedImages = mutableListOf<GalleryImage>()
                                            val successfulMoves = mutableListOf<Pair<GalleryImage, GalleryImage?>>()
                                            
                                            key.images.forEachIndexed { index, image ->
                                                actionProgress = (index + 1).toFloat() / total
                                                actionProgressText = "${index + 1} de $total"
                                                
                                                val movedImage = repository.moveImage(image, album.id)
                                                successfulMoves.add(image to movedImage)
                                                updatedImages.add(movedImage ?: image)
                                            }
                                            
                                            val anySuccess = successfulMoves.any { it.second != null }
                                            
                                            if (anySuccess) {
                                                val newAllImages = key.allImages.map { current ->
                                                    updatedImages.find { it.id == current.id } ?: current
                                                }
                                                
                                                // Actualizamos la entrada anterior en el backstack (el Detail si venía de ahí)
                                                if (backStack.size > 1 && backStack[backStack.size - 2] is Destination.Detail) {
                                                    val detailDest = backStack[backStack.size - 2] as Destination.Detail
                                                    val newCurrentImage = updatedImages.find { it.id == detailDest.image.id } ?: detailDest.image
                                                    backStack = backStack.dropLast(1).dropLast(1) + Destination.Detail(newCurrentImage, newAllImages)
                                                } else {
                                                    backStack = backStack.dropLast(1)
                                                }
                                                
                                                handler?.clearSelection()
                                                
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
