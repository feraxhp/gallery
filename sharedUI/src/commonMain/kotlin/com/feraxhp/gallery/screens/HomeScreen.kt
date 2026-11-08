package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feraxhp.gallery.components.NavigationItem
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

enum class HomeTab {
    Gallery, Albums
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    repository: ImageRepository,
    galleryViewModel: GalleryViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onNavigateToDetail: (GalleryImage, List<GalleryImage>) -> Unit,
    onNavigateToAlbumGallery: (Album) -> Unit,
    onNavigateToMoveToAlbum: (List<GalleryImage>, List<GalleryImage>) -> Unit,
    onShareImages: (List<GalleryImage>) -> Unit,
    onDeleteImages: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(HomeTab.Gallery) }
    
    val selectedIds by galleryViewModel.selectedImageIds.collectAsState()
    val allImages by galleryViewModel.images.collectAsState()
    val selectedImages = remember(selectedIds, allImages) {
        allImages.filter { it.id in selectedIds }
    }

    val title = if (selectedIds.isNotEmpty()) "${selectedIds.size} seleccionadas" 
                else if (currentTab == HomeTab.Gallery) "Fotos" 
                else "Álbumes"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                navigationIcon = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { galleryViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar selección")
                        }
                    }
                },
                actions = {
                    if (selectedImages.isNotEmpty()) {
                        IconButton(onClick = { onShareImages(selectedImages) }) {
                            Icon(Icons.Default.Share, contentDescription = "Compartir seleccionadas")
                        }
                        IconButton(onClick = { onNavigateToMoveToAlbum(selectedImages, allImages) }) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Mover seleccionadas")
                        }
                        IconButton(onClick = { onDeleteImages() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Borrar seleccionadas")
                        }
                    }
                }
            )
        }
    ) { padding ->
        val topPadding = padding.calculateTopPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topPadding + 20.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
                    .zIndex(1f)
            )

            // Contenido Principal
            Crossfade(
                targetState = currentTab, 
                label = "HomeTabCrossfade",
                animationSpec = tween(400)
            ) { tab ->
                when (tab) {
                    HomeTab.Gallery -> {
                        GalleryScreen(
                            viewModel = galleryViewModel,
                            onImageClick = onNavigateToDetail,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            topPadding = topPadding
                        )
                    }
                    HomeTab.Albums -> {
                        AlbumsScreen(
                            repository = repository,
                            onAlbumClick = onNavigateToAlbumGallery,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            topPadding = topPadding
                        )
                    }
                }
            }

            // Fondo del BottomBar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .zIndex(0.9f)
            )

            // BottomBar (Floating Toolbar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .offset(y = (-16).dp)
                    .padding(horizontal = 16.dp)
                    .zIndex(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    modifier = Modifier.wrapContentWidth(),
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    ),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    expandedShadowElevation = 6.dp,
                    content = {
                        NavigationItem(
                            selected = currentTab == HomeTab.Gallery,
                            label = "Fotos",
                            icon = Icons.Default.PhotoLibrary,
                            modifier = Modifier.width(100.dp),
                            onClick = { currentTab = HomeTab.Gallery }
                        )
                        NavigationItem(
                            selected = currentTab == HomeTab.Albums,
                            label = "Álbumes",
                            icon = Icons.Default.PhotoAlbum,
                            modifier = Modifier.width(100.dp),
                            onClick = { currentTab = HomeTab.Albums }
                        )
                    }
                )

                Spacer(Modifier.width(12.dp))

                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                    shadowElevation = 6.dp
                ) {
                    IconButton(
                        onClick = { /* TODO: Implement filter */ },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filtrar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
