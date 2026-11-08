package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.feraxhp.gallery.components.GalleryGrid
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.PickerConfig
import com.feraxhp.gallery.navigation.LocalIsBackgroundScene
import com.feraxhp.gallery.util.BackHandler
import com.feraxhp.gallery.viewmodel.GalleryViewModel

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumGalleryScreen(
    viewModel: GalleryViewModel,
    albumId: String,
    albumName: String,
    onBack: () -> Unit,
    onImageClick: (GalleryImage, List<GalleryImage>) -> Unit,
    onNavigateToMoveToAlbum: (List<GalleryImage>, List<GalleryImage>) -> Unit,
    onShareImages: (List<GalleryImage>) -> Unit,
    onDeleteImages: (List<GalleryImage>) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    pickerConfig: PickerConfig? = null,
    onPickImages: (List<GalleryImage>) -> Unit = {}
) {
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val deletedImageIds by viewModel.deletedImageIds.collectAsState()
    val isDeletedFromDetail by viewModel.isDeletedFromDetail.collectAsState()
    val selectedImageIds by viewModel.selectedImageIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    
    val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
    val isTargetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible

    val cornerRadius by animateDpAsState(
        targetValue = if (isTargetVisible && !isTransitionRunning) 0.dp else 16.dp,
        label = "cornerRadius"
    )

    LaunchedEffect(albumId, animatedVisibilityScope.transition.targetState) {
        if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible && images.isEmpty()) {
            viewModel.loadImages()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    val selectedImages = remember(selectedImageIds, images) {
        images.filter { it.id in selectedImageIds }
    }

    val isPickerMode = pickerConfig != null
    val isSinglePicker = pickerConfig != null && !pickerConfig.allowMultiple

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (selectedImageIds.isNotEmpty()) "${selectedImageIds.size} seleccionadas" else albumName) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                navigationIcon = {
                    if (selectedImageIds.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar selección")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    }
                },
                actions = {
                    if (isPickerMode && pickerConfig.allowMultiple && selectedImages.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = { onPickImages(selectedImages) },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Confirmar selección",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Listo (${selectedImages.size})")
                        }
                    } else if (!isPickerMode && selectedImages.isNotEmpty()) {
                        IconButton(onClick = { onShareImages(selectedImages) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir seleccionadas")
                        }
                        IconButton(onClick = { onNavigateToMoveToAlbum(selectedImages, images) }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Mover seleccionadas")
                        }
                        IconButton(onClick = { onDeleteImages(selectedImages) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar seleccionadas")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val isBackgroundScene = LocalIsBackgroundScene.current
        with(sharedTransitionScope) {
            GalleryGrid(
                images = images,
                isLoading = isLoading,
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refreshGallery() },
                onImageClick = { image, list ->
                    if (isSinglePicker) {
                        onPickImages(listOf(image))
                    } else {
                        onImageClick(image, list)
                    }
                },
                onImageLongClick = if (isSinglePicker) {
                    { image, list -> onImageClick(image, list) }
                } else null,
                enableMultiSelectDrag = !isSinglePicker,
                onToggleSelection = { viewModel.toggleSelection(it.id) },
                onSetSelection = { image, selected -> viewModel.setSelection(image.id, selected) },
                selectedImageIds = selectedImageIds,
                isSelectionMode = isSelectionMode,
                deletedImageIds = deletedImageIds,
                isDeletedFromDetail = isDeletedFromDetail,
                onClearDeletedState = { viewModel.clearDeletedState() },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                topPadding = padding.calculateTopPadding(),
                initialScrollIndex = viewModel.savedScrollIndex,
                initialScrollOffset = viewModel.savedScrollOffset,
                onScrollChanged = { index, offset ->
                    viewModel.savedScrollIndex = index
                    viewModel.savedScrollOffset = offset
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surface),
                gridModifier = Modifier
                    .then(
                        if (!isBackgroundScene) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "album-$albumId"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(500) },
                                clipInOverlayDuringTransition = OverlayClip(
                                    RoundedCornerShape(cornerRadius)
                                )
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(RoundedCornerShape(cornerRadius))
            )
        }
    }
}
