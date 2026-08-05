package com.feraxhp.gallery.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.feraxhp.gallery.util.rememberVideoModel
import com.feraxhp.gallery.viewmodel.GalleryViewModel

@Composable
fun MoveToAlbumScreen(
    repository: ImageRepository,
    onAlbumSelected: (Album) -> Unit
) {
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
    val albums by viewModel.albums.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadAlbums()
    }

    if (isLoading && albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumSelected(album) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val model = if (album.coverUri.contains("video", ignoreCase = true)) {
                        rememberVideoModel(album.coverUri, null)
                    } else {
                        album.coverUri
                    }
                    AsyncImage(
                        model = model,
                        contentDescription = album.name,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${album.imageCount} elementos",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
