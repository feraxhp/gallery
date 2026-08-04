package com.feraxhp.gallery.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun GalleryScreen(repository: ImageRepository, albumId: String? = null) {
    val viewModel: GalleryViewModel = viewModel { GalleryViewModel(repository) }
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val groupedImages = remember(images) {
        images.groupBy {
            Instant.fromEpochSeconds(it.dateAdded)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        }
    }

    LaunchedEffect(albumId) {
        viewModel.loadImages(albumId)
    }

    if (isLoading && images.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            groupedImages.forEach { (date, imagesInDate) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val monthName = when (date.month) {
                        kotlinx.datetime.Month.JANUARY -> "enero"
                        kotlinx.datetime.Month.FEBRUARY -> "febrero"
                        kotlinx.datetime.Month.MARCH -> "marzo"
                        kotlinx.datetime.Month.APRIL -> "abril"
                        kotlinx.datetime.Month.MAY -> "mayo"
                        kotlinx.datetime.Month.JUNE -> "junio"
                        kotlinx.datetime.Month.JULY -> "julio"
                        kotlinx.datetime.Month.AUGUST -> "agosto"
                        kotlinx.datetime.Month.SEPTEMBER -> "septiembre"
                        kotlinx.datetime.Month.OCTOBER -> "octubre"
                        kotlinx.datetime.Month.NOVEMBER -> "noviembre"
                        kotlinx.datetime.Month.DECEMBER -> "diciembre"
                    }
                    Text(
                        text = "${date.day} de $monthName de ${date.year}",
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(imagesInDate, key = { it.id }) { image ->
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
