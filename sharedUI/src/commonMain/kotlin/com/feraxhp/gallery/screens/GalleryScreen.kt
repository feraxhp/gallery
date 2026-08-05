package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.feraxhp.gallery.components.ShatterEffect
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.rememberVideoModel
import com.feraxhp.gallery.util.toImageBitmap
import com.feraxhp.gallery.viewmodel.GalleryViewModel
import gallery.sharedui.generated.resources.Res
import gallery.sharedui.generated.resources.ic_cyclone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource

private data class ShatterData(
    val bitmap: ImageBitmap,
    val offset: IntOffset,
    val size: androidx.compose.ui.unit.IntSize
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    albumId: String? = null,
    onImageClick: (GalleryImage, List<GalleryImage>) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val images by viewModel.images.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val deletedImageId by viewModel.deletedImageId.collectAsState()
    
    // Offset de la propia galería respecto al root para corregir el posicionamiento de la animación
    var galleryOffset by remember { mutableStateOf(Offset.Zero) }

    // Para rastrear posiciones y bitmaps de las imágenes
    val imageBounds = remember { mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    val imageBitmaps = remember { mutableStateMapOf<Long, ImageBitmap>() }
    
    // Estado para la animación activa
    var activeShatterEffect by remember { mutableStateOf<ShatterData?>(null) }
    
    LaunchedEffect(deletedImageId) {
        val id = deletedImageId
        if (id != null) {
            val bounds = imageBounds[id]
            val bitmap = imageBitmaps[id]
            
            if (bounds != null && bitmap != null) {
                // Esperar a que la transición sea visible (comienza antes de que termine del todo)
                snapshotFlow { animatedVisibilityScope.transition.targetState == EnterExitState.Visible }
                    .filter { it }
                    .first()
                
                delay(390) // Retraso de 50ms para sincronizar mejor
                
                activeShatterEffect = ShatterData(
                    bitmap = bitmap,
                    offset = IntOffset(bounds.left.toInt(), bounds.top.toInt()),
                    size = androidx.compose.ui.unit.IntSize(bounds.width.toInt(), bounds.height.toInt())
                )
            }
            // Limpiamos el estado en el ViewModel para que no se repita
            viewModel.clearDeletedState()
        }
    }

    val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
    val isTargetVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible

    val cornerRadius by animateDpAsState(
        targetValue = if (isTargetVisible && !isTransitionRunning) 0.dp else 16.dp,
        label = "cornerRadius"
    )

    val groupedImages = remember(images) {
        images.groupBy {
            Instant.fromEpochSeconds(it.dateAdded)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        }
    }

    LaunchedEffect(albumId, animatedVisibilityScope.transition.targetState) {
        if (animatedVisibilityScope.transition.targetState == EnterExitState.Visible) {
            viewModel.loadImages(albumId)
        }
    }

    if (isLoading && images.isEmpty()) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { galleryOffset = it.positionInRoot() }, contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    } else {
        with(sharedTransitionScope) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { galleryOffset = it.positionInRoot() }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (albumId != null) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "album-$albumId"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(500) },
                                clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerRadius))
                            )
                        } else Modifier
                    )
                    .clip(RoundedCornerShape(cornerRadius)),
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
                            modifier = Modifier
                                .animateItem()
                                .padding(vertical = 12.dp, horizontal = 8.dp),
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
                                    .animateItem()
                                    .aspectRatio(1f)
                                    .fillMaxWidth()
                                    .onGloballyPositioned {
                                        val rootPos = it.positionInRoot()
                                        imageBounds[image.id] = androidx.compose.ui.geometry.Rect(
                                            offset = Offset(rootPos.x - galleryOffset.x, rootPos.y - galleryOffset.y),
                                            size = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())
                                        )
                                    }
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { onImageClick(image, images) }
                            ) {
                                AsyncImage(
                                    model = model,
                                    onState = { state ->
                                        if (state is AsyncImagePainter.State.Success) {
                                            state.result.image.toImageBitmap()?.let {
                                                imageBitmaps[image.id] = it
                                            }
                                        }
                                    },
                                    contentDescription = image.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .sharedBounds(
                                            sharedContentState = rememberSharedContentState(key = "image-${image.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            boundsTransform = { _, _ -> tween(500) },
                                            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(12.dp))
                                        )
                                        .clip(RoundedCornerShape(12.dp)),
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
                                            .background(
                                                Color.Black.copy(alpha = 0.4f),
                                                CircleShape
                                            ),
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
                
            // El ShatterEffect se dibuja encima de todo en un Box de pantalla completa
            Box(Modifier.fillMaxSize()) {
                activeShatterEffect?.let { data ->
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    ShatterEffect(
                        bitmap = data.bitmap,
                        modifier = Modifier
                            .offset(
                                x = with(density) { data.offset.x.toDp() },
                                y = with(density) { data.offset.y.toDp() }
                            )
                            .size(
                                width = with(density) { data.size.width.toDp() },
                                height = with(density) { data.size.height.toDp() }
                            ),
                        onAnimationEnd = {
                            activeShatterEffect = null
                        }
                    )
                }
            }
        }
    }
}
}
