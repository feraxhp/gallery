package com.feraxhp.gallery.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import com.feraxhp.ktheme.DynamicTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(repository: ImageRepository) {
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadImages()
    }

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
                modifier = Modifier.fillMaxSize().padding(padding),
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
                            .fillMaxWidth(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}
