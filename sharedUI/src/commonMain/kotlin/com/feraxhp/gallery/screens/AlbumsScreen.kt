package com.feraxhp.gallery.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.feraxhp.gallery.model.Album
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.viewmodel.GalleryViewModel

@Composable
fun AlbumsScreen(
    repository: ImageRepository,
    onAlbumClick: (Album) -> Unit
) {
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAlbums()
    }

    if (isLoading && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Column(
                    modifier = Modifier.clickable { onAlbumClick(album) }
                ) {
                    AsyncImage(
                        model = album.coverUri,
                        contentDescription = album.name,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium),
                        contentScale = ContentScale.Crop
                    )
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
