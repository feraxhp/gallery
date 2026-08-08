package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.feraxhp.gallery.components.GalleryGrid
import com.feraxhp.gallery.components.ShatterEffect
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.ShatterData
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.BackHandler
import com.feraxhp.gallery.util.rememberVideoModel
import com.feraxhp.gallery.util.toImageBitmap
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import gallery.sharedui.generated.resources.Res
import gallery.sharedui.generated.resources.ic_cyclone
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Duration.Companion.milliseconds


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
    val selectedImageIds by viewModel.selectedImageIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    
    val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
    val isTargetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible

    val cornerRadius by animateDpAsState(
        targetValue = if (isTargetVisible && !isTransitionRunning) 0.dp else 16.dp,
        label = "cornerRadius"
    )

    LaunchedEffect(animatedVisibilityScope.transition.targetState) {
        if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible) {
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
        onClearDeletedState = { viewModel.clearDeletedState() },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        topPadding = topPadding,
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface),
        gridModifier = Modifier.clip(RoundedCornerShape(cornerRadius))
    )
}
