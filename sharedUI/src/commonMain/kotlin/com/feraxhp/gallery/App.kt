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

import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.metadata
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.feraxhp.gallery.navigation.Destination
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.screens.AlbumsScreen
import com.feraxhp.gallery.screens.DetailScreen
import com.feraxhp.gallery.screens.GalleryScreen
import com.feraxhp.gallery.screens.PermissionsScreen
import com.feraxhp.ktheme.DynamicTheme

@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    ExperimentalSharedTransitionApi::class
)
@Composable
fun App(
    repository: ImageRepository,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var backStack by remember {
        mutableStateOf(
            listOf<Destination>(
                if (hasPermission) Destination.Gallery else Destination.Permissions
            )
        )
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && backStack.lastOrNull() == Destination.Permissions) {
            backStack = listOf(Destination.Gallery)
        }
    }

    val currentDestination = backStack.lastOrNull()
    val isDetail = currentDestination is Destination.Detail

    val appBarContainerColor by animateColorAsState(if (isDetail) Color.Black else MaterialTheme.colorScheme.surface)
    val appBarContentColor by animateColorAsState(if (isDetail) Color.White else MaterialTheme.colorScheme.onSurface)
    val scaffoldContainerColor by animateColorAsState(if (isDetail) Color.Black else MaterialTheme.colorScheme.background)

    DynamicTheme {
        Scaffold(
            containerColor = scaffoldContainerColor,
            topBar = {
                val title = when (currentDestination) {
                    Destination.Permissions -> "Permisos"
                    Destination.Gallery -> "Fotos"
                    Destination.Albums -> "Álbumes"
                    is Destination.AlbumGallery -> currentDestination.albumName
                    is Destination.Detail -> "Detalle"
                    null -> ""
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
                        if (backStack.size > 1) {
                            IconButton(onClick = { backStack = backStack.dropLast(1) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Atrás"
                                )
                            }
                        }
                    }
                )
            },
            bottomBar = {
                val showBottomBar = hasPermission && (currentDestination == Destination.Gallery || currentDestination == Destination.Albums || isDetail)
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = if (isDetail) Color.Black else MaterialTheme.colorScheme.surface,
                        contentColor = if (isDetail) Color.White else MaterialTheme.colorScheme.onSurface
                    ) {
                        if (!isDetail) {
                            NavigationBarItem(
                                selected = currentDestination == Destination.Gallery,
                                onClick = {
                                    if (currentDestination != Destination.Gallery) {
                                        backStack = listOf(Destination.Gallery)
                                    }
                                },
                                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = "Fotos") },
                                label = { Text("Fotos") }
                            )
                            NavigationBarItem(
                                selected = currentDestination == Destination.Albums || currentDestination is Destination.AlbumGallery,
                                onClick = {
                                    if (currentDestination != Destination.Albums) {
                                        backStack = listOf(Destination.Albums)
                                    }
                                },
                                icon = { Icon(Icons.Default.Collections, contentDescription = "Álbumes") },
                                label = { Text("Álbumes") }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            SharedTransitionLayout {
                NavDisplay(
                    modifier = Modifier.padding(padding),
                    backStack = backStack,
                    sharedTransitionScope = this,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack = backStack.dropLast(1)
                        }
                    },
                    entryProvider = { key: Destination ->
                        when (key) {
                            Destination.Permissions -> NavEntry(key) {
                                PermissionsScreen(onRequestPermission = onRequestPermission)
                            }
                            Destination.Gallery -> NavEntry(
                                key,
                                metadata = metadata {
                                    put(NavDisplay.TransitionKey) {
                                        fadeIn() togetherWith fadeOut() + ExitTransition.KeepUntilTransitionsFinished
                                    }
                                }
                            ) {
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                GalleryScreen(
                                    repository = repository,
                                    onImageClick = { image ->
                                        backStack = backStack + Destination.Detail(image)
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                            Destination.Albums -> NavEntry(key) {
                                AlbumsScreen(
                                    repository = repository,
                                    onAlbumClick = { album ->
                                        backStack = backStack + Destination.AlbumGallery(album.id, album.name)
                                    }
                                )
                            }
                            is Destination.AlbumGallery -> NavEntry(
                                key,
                                metadata = metadata {
                                    put(NavDisplay.TransitionKey) {
                                        fadeIn() togetherWith fadeOut() + ExitTransition.KeepUntilTransitionsFinished
                                    }
                                }
                            ) {
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                GalleryScreen(
                                    repository = repository,
                                    albumId = key.albumId,
                                    onImageClick = { image ->
                                        backStack = backStack + Destination.Detail(image)
                                    },
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                            is Destination.Detail -> NavEntry(
                                key,
                                metadata = metadata {
                                    put(NavDisplay.TransitionKey) {
                                        fadeIn() togetherWith fadeOut() + ExitTransition.KeepUntilTransitionsFinished
                                    }
                                    put(NavDisplay.PopTransitionKey) {
                                        fadeIn() togetherWith fadeOut() + ExitTransition.KeepUntilTransitionsFinished
                                    }
                                }
                            ) {
                                val animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                DetailScreen(
                                    image = key.image,
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    onBack = { backStack = backStack.dropLast(1) }
                                )
                            }
                        }
                    }
                )
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
    }
    App(
        repository = mockRepository,
        hasPermission = true,
        onRequestPermission = {}
    )
}
