package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.rememberVideoModel
import com.feraxhp.gallery.viewmodel.AlbumsViewModel

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumsScreen(
    repository: ImageRepository,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onAlbumClick: (Album) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val viewModel: AlbumsViewModel = viewModel { AlbumsViewModel(repository) }
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(animatedVisibilityScope.transition.targetState) {
        if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible) {
            viewModel.loadAlbums()
        }
    }

    val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
    val isTargetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible

    val cornerRadius by animateDpAsState(
        targetValue = if (isTargetVisible && !isTransitionRunning) 0.dp else 16.dp,
        label = "cornerRadius"
    )

    if (isLoading && albums.isEmpty()) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAlbums() },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surface),
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = topPadding)
                        .zIndex(10f)
                )
            }
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp + topPadding,
                    end = 16.dp,
                    bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues()
                        .calculateBottomPadding()
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    with(sharedTransitionScope) {
                        Column(
                            modifier = Modifier
                                .sharedBounds(
                                    sharedContentState = rememberSharedContentState(key = "album-${album.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ -> tween(500) }
                                )
                                .clickable { onAlbumClick(album) }
                        ) {
                            val model = if (album.coverUri.contains("video", ignoreCase = true)) {
                                rememberVideoModel(album.coverUri, null)
                            } else {
                                album.coverUri
                            }
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.extraLarge)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                if (album.coverUri.isEmpty()) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoAlbum,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                } else {
                                    LoadingIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                    AsyncImage(
                                        model = model,
                                        contentDescription = album.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(MaterialTheme.shapes.extraLarge),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = "${album.imageCount} fotos",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
