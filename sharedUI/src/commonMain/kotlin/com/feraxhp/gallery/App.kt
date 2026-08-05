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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import com.feraxhp.gallery.components.NavigationItem
import com.feraxhp.gallery.navigation.Destination
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.screens.AlbumsScreen
import com.feraxhp.gallery.screens.DetailScreen
import com.feraxhp.gallery.screens.GalleryScreen
import com.feraxhp.gallery.screens.MoveToAlbumScreen
import com.feraxhp.gallery.screens.PermissionsScreen
import com.feraxhp.gallery.util.SetSystemBarsColor
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import com.feraxhp.ktheme.DynamicTheme
import kotlinx.coroutines.launch

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
    onRequestReadPermission: () -> Unit,
    onRequestWritePermission: () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val galleryViewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
    var backStack by remember {
        mutableStateOf(
            listOf<Destination>(
                if (hasReadPermission && hasWritePermission) Destination.Gallery else Destination.Permissions
            )
        )
    }

    val currentReadPermission by rememberUpdatedState(hasReadPermission)
    val currentWritePermission by rememberUpdatedState(hasWritePermission)

    LaunchedEffect(hasReadPermission, hasWritePermission) {
        if (hasReadPermission && hasWritePermission && backStack.lastOrNull() == Destination.Permissions) {
            backStack = listOf(Destination.Gallery)
        }
    }

    val currentDestination = backStack.lastOrNull()
    var isDetailActive by remember { mutableStateOf(false) }
    var currentImageInDetail by remember {
        mutableStateOf<com.feraxhp.gallery.model.GalleryImage?>(
            null
        )
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SetSystemBarsColor(isDark = isDetailActive || isSystemDark)

    if (showDeleteConfirmation && currentImageInDetail != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Borrar imagen") },
            text = { Text("¿Estás seguro de que quieres borrar esta imagen?") },
            confirmButton = {
                TextButton(onClick = {
                    val imageToDelete = currentImageInDetail!!
                    scope.launch {
                        galleryViewModel.markAsDeleted(imageToDelete.id)
                        repository.deleteImage(imageToDelete)
                        showDeleteConfirmation = false
                        // Si borramos, volvemos atrás
                        if (backStack.size > 1) {
                            backStack = backStack.dropLast(1)
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
        val appBarContainerColor by animateColorAsState(if (isDetailActive) Color.Black else MaterialTheme.colorScheme.surface)
        val appBarContentColor by animateColorAsState(if (isDetailActive) Color.White else MaterialTheme.colorScheme.onSurface)
        val scaffoldContainerColor by animateColorAsState(if (isDetailActive) Color.Black else MaterialTheme.colorScheme.background)

        Scaffold(
            containerColor = scaffoldContainerColor,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                val title = when (currentDestination) {
                    Destination.Permissions -> ""
                    Destination.Gallery -> "Fotos"
                    Destination.Albums -> "Álbumes"
                    is Destination.AlbumGallery -> currentDestination.albumName
                    is Destination.Detail -> "Detalle"
                    is Destination.MoveToAlbum -> "Mover a álbum"
                    else -> ""
                }
                TopAppBar(
                    title = { Text(title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = appBarContainerColor,
                        titleContentColor = appBarContentColor,
                        navigationIconContentColor = appBarContentColor,
                        actionIconContentColor = appBarContentColor
                    ),
                    navigationIcon = {
                        if (backStack.size > 1 && currentDestination != Destination.Albums) {
                            IconButton(onClick = {
                                if (backStack.size > 1) {
                                    backStack = backStack.dropLast(1)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás"
                                )
                            }
                        }
                    },
                    actions = {
                        if (currentDestination is Destination.Detail && isDetailActive && currentImageInDetail != null) {
                            IconButton(onClick = {
                                currentImageInDetail?.let {
                                    repository.shareImage(it)
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Compartir")
                            }
                            IconButton(onClick = {
                                currentImageInDetail?.let {
                                    backStack = backStack + Destination.MoveToAlbum(
                                        it,
                                        (currentDestination as Destination.Detail).allImages
                                    )
                                }
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DriveFileMove,
                                    contentDescription = "Mover"
                                )
                            }
                            IconButton(onClick = {
                                showDeleteConfirmation = true
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                val isActuallyInDetail = currentDestination is Destination.Detail && isDetailActive
                val showBottomBar = hasReadPermission && (
                        currentDestination == Destination.Gallery ||
                        currentDestination == Destination.Albums ||
                                (currentDestination is Destination.Detail && !isDetailActive)
                        )

                if (showBottomBar && !isActuallyInDetail) {
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
                }

                AnimatedVisibility (
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .offset(y = (-16).dp)
                        .zIndex(1f),
                    visible = showBottomBar && !isActuallyInDetail,
                    enter = slideInVertically(
                        initialOffsetY = { it / 2 }
                    ) + fadeIn( initialAlpha = 0.3f ),
                    exit = slideOutVertically(
                        targetOffsetY = { it / 2 }
                    ) + fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
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
                                    selected = currentDestination == Destination.Gallery,
                                    label = "Fotos",
                                    icon = Icons.Default.PhotoLibrary,
                                    onClick = {
                                        if (currentDestination != Destination.Gallery) {
                                            backStack = listOf(Destination.Gallery)
                                        }
                                    }
                                )
                                Spacer(Modifier.width(4.dp))
                                NavigationItem(
                                    selected = currentDestination == Destination.Albums || currentDestination is Destination.AlbumGallery,
                                    label = "Álbumes",
                                    icon = Icons.Default.PhotoAlbum,
                                    onClick = {
                                        if (currentDestination != Destination.Albums) {
                                            backStack = listOf(Destination.Gallery, Destination.Albums)
                                        }
                                    }
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
                SharedTransitionLayout {
                    NavDisplay(
                        modifier = Modifier,
                        backStack = backStack,
                        sharedTransitionScope = this,
                        onBack = {
                            if (backStack.size > 1) {
                                backStack = backStack.dropLast(1)
                            }
                        },
                        entryProvider = { key: Destination ->
                            when (key) {
                                Destination.Permissions -> NavEntry(
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
                                    PermissionsScreen(
                                        hasReadPermission = currentReadPermission,
                                        hasWritePermission = currentWritePermission,
                                        onRequestReadPermission = onRequestReadPermission,
                                        onRequestWritePermission = onRequestWritePermission,
                                        onContinue = {
                                            backStack = listOf(Destination.Gallery)
                                        }
                                    )
                                }

                                Destination.Gallery -> NavEntry(
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
                                    LaunchedEffect(Unit) { isDetailActive = false }
                                    val animatedVisibilityScope =
                                        LocalNavAnimatedContentScope.current
                                    GalleryScreen(
                                        viewModel = galleryViewModel,
                                        onImageClick = { image, allImages ->
                                            backStack =
                                                backStack + Destination.Detail(image, allImages)
                                        },
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }

                                Destination.Albums -> NavEntry(
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
                                    LaunchedEffect(Unit) { isDetailActive = false }
                                    val animatedVisibilityScope =
                                        LocalNavAnimatedContentScope.current
                                    AlbumsScreen(
                                        repository = repository,
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        onAlbumClick = { album ->
                                            backStack = backStack + Destination.AlbumGallery(
                                                album.id,
                                                album.name
                                            )
                                        }
                                    )
                                }

                                is Destination.AlbumGallery -> NavEntry(
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
                                    LaunchedEffect(Unit) { isDetailActive = false }
                                    val animatedVisibilityScope =
                                        LocalNavAnimatedContentScope.current
                                    GalleryScreen(
                                        viewModel = galleryViewModel,
                                        albumId = key.albumId,
                                        onImageClick = { image, allImages ->
                                            backStack =
                                                backStack + Destination.Detail(image, allImages)
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
                                    val animatedVisibilityScope =
                                        LocalNavAnimatedContentScope.current
                                    val isTargetVisible =
                                        animatedVisibilityScope.transition.targetState == EnterExitState.Visible
                                    LaunchedEffect(isTargetVisible) {
                                        isDetailActive = isTargetVisible
                                    }
                                    DetailScreen(
                                        images = key.allImages,
                                        initialIndex = key.allImages.indexOfFirst { it.id == key.image.id }
                                            .coerceAtLeast(0),
                                        sharedTransitionScope = this@SharedTransitionLayout,
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        repository = repository,
                                        onImageChange = { currentImageInDetail = it },
                                        onBack = {
                                            if (backStack.size > 1) {
                                                backStack = backStack.dropLast(1)
                                            }
                                        }
                                    )
                                }

                                is Destination.MoveToAlbum -> NavEntry(
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
                                    MoveToAlbumScreen(
                                        repository = repository,
                                        onAlbumSelected = { album ->
                                            scope.launch {
                                                val updatedImage =
                                                    repository.moveImage(key.image, album.id)
                                                if (updatedImage != null) {
                                                    val newAllImages = key.allImages.map {
                                                        if (it.id == updatedImage.id) updatedImage else it
                                                    }
                                                    // Actualizamos la entrada anterior en el backstack (el Detail)
                                                    // y quitamos la actual (MoveToAlbum)
                                                    backStack =
                                                        backStack.dropLast(1).let { list ->
                                                            if (list.isNotEmpty() && list.last() is Destination.Detail) {
                                                                list.dropLast(1) + Destination.Detail(
                                                                    updatedImage,
                                                                    newAllImages
                                                                )
                                                            } else {
                                                                list
                                                            }
                                                        }
                                                } else {
                                                    if (backStack.size > 1) {
                                                        backStack = backStack.dropLast(1)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun AppPreview() {
    val mockRepository = object : ImageRepository {
        override suspend fun getImages(): List<com.feraxhp.gallery.model.GalleryImage> = emptyList()
        override suspend fun getImagesByAlbum(albumId: String): List<com.feraxhp.gallery.model.GalleryImage> = emptyList()
        override suspend fun getAlbums(): List<com.feraxhp.gallery.model.Album> = emptyList()
        override suspend fun getImageById(id: Long, type: com.feraxhp.gallery.model.MediaType): com.feraxhp.gallery.model.GalleryImage? = null
        override suspend fun deleteImage(image: com.feraxhp.gallery.model.GalleryImage): Boolean = true
        override suspend fun moveImage(image: com.feraxhp.gallery.model.GalleryImage, albumId: String): com.feraxhp.gallery.model.GalleryImage? = null
        override suspend fun copyImage(image: com.feraxhp.gallery.model.GalleryImage): Boolean = true
        override fun openInFileManager(path: String) {}
        override fun shareImage(image: com.feraxhp.gallery.model.GalleryImage) {}
    }
    App(
        repository = mockRepository,
        hasReadPermission = true,
        hasWritePermission = true,
        onRequestReadPermission = {},
        onRequestWritePermission = {}
    )
}
