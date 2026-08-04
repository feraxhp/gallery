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

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.feraxhp.gallery.navigation.Destination
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.screens.AlbumsScreen
import com.feraxhp.gallery.screens.GalleryScreen
import com.feraxhp.gallery.screens.PermissionsScreen
import com.feraxhp.ktheme.DynamicTheme

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    DynamicTheme {
        Scaffold(
            topBar = {
                val title = when (currentDestination) {
                    Destination.Permissions -> "Permisos"
                    Destination.Gallery -> "Fotos"
                    Destination.Albums -> "Álbumes"
                    is Destination.AlbumGallery -> currentDestination.albumName
                    null -> ""
                }
                TopAppBar(
                    title = { Text(title) },
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
                if (hasPermission && (currentDestination == Destination.Gallery || currentDestination == Destination.Albums)) {
                    NavigationBar {
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
        ) { padding ->
            NavDisplay(
                modifier = Modifier.padding(padding),
                backStack = backStack,
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
                        Destination.Gallery -> NavEntry(key) {
                            GalleryScreen(repository = repository)
                        }
                        Destination.Albums -> NavEntry(key) {
                            AlbumsScreen(
                                repository = repository,
                                onAlbumClick = { album ->
                                    backStack = backStack + Destination.AlbumGallery(album.id, album.name)
                                }
                            )
                        }
                        is Destination.AlbumGallery -> NavEntry(key) {
                            GalleryScreen(
                                repository = repository,
                                albumId = key.albumId
                            )
                        }
                    }
                }
            )
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
