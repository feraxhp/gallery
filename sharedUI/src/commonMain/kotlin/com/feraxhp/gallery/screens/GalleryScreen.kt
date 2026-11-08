package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.feraxhp.gallery.components.GalleryGrid
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.util.BackHandler
import com.feraxhp.gallery.viewmodel.GalleryViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onImageClick: (GalleryImage, List<GalleryImage>) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
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

    LaunchedEffect(animatedVisibilityScope.transition.targetState) {
        if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible && images.isEmpty()) {
            viewModel.loadImages()
        }
    }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    GalleryGrid(
        images = images,
        isLoading = isLoading,
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refreshGallery() },
        onImageClick = onImageClick,
        onToggleSelection = { viewModel.toggleSelection(it.id) },
        onSetSelection = { image, selected -> viewModel.setSelection(image.id, selected) },
        selectedImageIds = selectedImageIds,
        isSelectionMode = isSelectionMode,
        deletedImageIds = deletedImageIds,
        isDeletedFromDetail = isDeletedFromDetail,
        onClearDeletedState = { viewModel.clearDeletedState() },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        topPadding = topPadding,
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
        gridModifier = Modifier.clip(RoundedCornerShape(cornerRadius))
    )
}
