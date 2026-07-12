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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import com.feraxhp.ktheme.DynamicTheme
import androidx.compose.ui.tooling.preview.Preview
import com.feraxhp.gallery.model.GalleryImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(repository: ImageRepository) {
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadImages()
    }

    DynamicTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Galería MVVM") })
            }
        ) { padding ->
            if (isLoading && images.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                    ,
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        AsyncImage(
                            model = image.uri,
                            contentDescription = image.name,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth()
                                .clip(shape = MaterialTheme.shapes.medium)
                            ,
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun AppPreview() {
    val mockRepository = object : ImageRepository {
        override suspend fun getImages(): List<GalleryImage> = listOf(
            GalleryImage(1, "https://example.com/1.jpg", "Image 1"),
            GalleryImage(2, "https://example.com/2.jpg", "Image 2")
        )
    }
    App(mockRepository)
}
