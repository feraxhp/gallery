package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
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
import com.feraxhp.gallery.components.NavigationItem
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaFilter
import com.feraxhp.gallery.model.PickerConfig
import com.feraxhp.gallery.model.SortOrder
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.viewmodel.GalleryViewModel

enum class HomeTab {
    Gallery, Albums
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
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
    onDeleteImages: () -> Unit,
    pickerConfig: PickerConfig? = null,
    onPickImages: (List<GalleryImage>) -> Unit = {},
    onCancelPicker: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(HomeTab.Gallery) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    
    val selectedIds by galleryViewModel.selectedImageIds.collectAsState()
    val allImages by galleryViewModel.images.collectAsState()
    val mediaFilter by galleryViewModel.mediaFilter.collectAsState()
    val sortOrder by galleryViewModel.sortOrder.collectAsState()

    val selectedImages = remember(selectedIds, allImages) {
        allImages.filter { it.id in selectedIds }
    }

    val isPickerMode = pickerConfig != null
    val isSinglePicker = pickerConfig != null && !pickerConfig.allowMultiple

    val title = when {
        selectedIds.isNotEmpty() -> "${selectedIds.size} seleccionadas"
        isPickerMode -> if (pickerConfig.allowMultiple) "Seleccionar elementos" else "Seleccionar elemento"
        currentTab == HomeTab.Gallery -> "Fotos"
        else -> "Álbumes"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (isSinglePicker && currentTab == HomeTab.Gallery) {
                            Text(
                                text = "Toca para elegir • Mantén presionado para ver",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                ),
                navigationIcon = {
                    if (selectedIds.isNotEmpty()) {
                        IconButton(onClick = { galleryViewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar selección")
                        }
                    } else if (isPickerMode) {
                        IconButton(onClick = onCancelPicker) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar")
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
                            onImageClick = { image, list ->
                                if (isSinglePicker) {
                                    onPickImages(listOf(image))
                                } else {
                                    onNavigateToDetail(image, list)
                                }
                            },
                            onImageLongClick = if (isSinglePicker) {
                                { image, list -> onNavigateToDetail(image, list) }
                            } else null,
                            enableMultiSelectDrag = !isSinglePicker,
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

                val hasActiveFilterOrSort = mediaFilter != MediaFilter.ALL || sortOrder != SortOrder.DATE_DESC
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = if (hasActiveFilterOrSort) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    },
                    shadowElevation = 6.dp
                ) {
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Filtrar y ordenar",
                            tint = if (hasActiveFilterOrSort) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            if (showFilterSheet) {
                FilterAndSortBottomSheet(
                    currentFilter = mediaFilter,
                    currentSort = sortOrder,
                    onSelectFilter = { galleryViewModel.setMediaFilter(it) },
                    onSelectSort = { galleryViewModel.setSortOrder(it) },
                    onReset = {
                        galleryViewModel.setMediaFilter(MediaFilter.ALL)
                        galleryViewModel.setSortOrder(SortOrder.DATE_DESC)
                    },
                    onDismiss = { showFilterSheet = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterAndSortBottomSheet(
    currentFilter: MediaFilter,
    currentSort: SortOrder,
    onSelectFilter: (MediaFilter) -> Unit,
    onSelectSort: (SortOrder) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtros y orden",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (currentFilter != MediaFilter.ALL || currentSort != SortOrder.DATE_DESC) {
                    TextButton(onClick = onReset) {
                        Text("Restablecer")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tipo de contenido",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentFilter == MediaFilter.ALL,
                        onClick = { onSelectFilter(MediaFilter.ALL) },
                        label = { Text("Todo") },
                        leadingIcon = if (currentFilter == MediaFilter.ALL) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = currentFilter == MediaFilter.IMAGES,
                        onClick = { onSelectFilter(MediaFilter.IMAGES) },
                        label = { Text("Fotos") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentFilter == MediaFilter.IMAGES) Icons.Default.Check else Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = currentFilter == MediaFilter.VIDEOS,
                        onClick = { onSelectFilter(MediaFilter.VIDEOS) },
                        label = { Text("Videos") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentFilter == MediaFilter.VIDEOS) Icons.Default.Check else Icons.Default.Videocam,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    FilterChip(
                        selected = currentFilter == MediaFilter.MOTION_PHOTOS,
                        onClick = { onSelectFilter(MediaFilter.MOTION_PHOTOS) },
                        label = { Text("Motion Photos") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentFilter == MediaFilter.MOTION_PHOTOS) Icons.Default.Check else Icons.Default.MotionPhotosOn,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ordenar por",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sortOptions = listOf(
                        SortOrder.DATE_DESC to "Más recientes",
                        SortOrder.DATE_ASC to "Más antiguas",
                        SortOrder.SIZE_DESC to "Mayor tamaño",
                        SortOrder.SIZE_ASC to "Menor tamaño",
                        SortOrder.NAME_ASC to "Nombre (A-Z)"
                    )
                    sortOptions.forEach { (order, label) ->
                        FilterChip(
                            selected = currentSort == order,
                            onClick = { onSelectSort(order) },
                            label = { Text(label) },
                            leadingIcon = if (currentSort == order) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}
