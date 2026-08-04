package com.feraxhp.gallery.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.rememberVideoModel
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import gallery.sharedui.generated.resources.Res
import gallery.sharedui.generated.resources.ic_cyclone
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    repository: ImageRepository,
    albumId: String? = null,
    onImageClick: (GalleryImage) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
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
                    val model = if (image.type == MediaType.VIDEO) {
                        rememberVideoModel(image.uri, image.duration)
                    } else {
                        image.uri
                    }
                    with(sharedTransitionScope) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onImageClick(image) }
                        ) {
                            AsyncImage(
                                model = model,
                                contentDescription = image.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "image-${image.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    ),
                                contentScale = ContentScale.Crop
                            )

                            if (image.type == MediaType.VIDEO) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .size(24.dp),
                                    tint = Color.White
                                )
                            } else if (image.isMotionPhoto) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.ic_cyclone),
                                        contentDescription = "Motion Photo",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
