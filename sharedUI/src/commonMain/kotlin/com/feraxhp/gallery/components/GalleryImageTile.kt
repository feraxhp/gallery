package com.feraxhp.gallery.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.util.rememberVideoModel
import com.feraxhp.gallery.util.toImageBitmap
import gallery.sharedui.generated.resources.Res
import gallery.sharedui.generated.resources.ic_cyclone
import io.ktor.utils.io.bits.lowInt
import org.jetbrains.compose.resources.painterResource

val shapes = listOf(
    MaterialShapes.Bun,
    MaterialShapes.Square,
    MaterialShapes.Diamond,
    MaterialShapes.Arrow,
    MaterialShapes.Oval,
    MaterialShapes.Pill,
    MaterialShapes.ClamShell,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Gem,
    MaterialShapes.Ghostish,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Pentagon,
)


@Suppress("RememberReturnType")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryImageTile(
    image: GalleryImage,
    allImages: List<GalleryImage>,
    onImageClick: (GalleryImage, List<GalleryImage>) -> Unit,
    onPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onBitmapLoaded: (ImageBitmap) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val model = if (image.type == MediaType.VIDEO) {
        rememberVideoModel(image.uri, image.duration)
    } else {
        image.uri
    }

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .onGloballyPositioned {
                    onPositioned(
                        androidx.compose.ui.geometry.Rect(
                            offset = androidx.compose.ui.geometry.Offset(
                                it.positionInRoot().x,
                                it.positionInRoot().y
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                it.size.width.toFloat(),
                                it.size.height.toFloat()
                            )
                        )
                    )
                }
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onImageClick(image, allImages) },
            contentAlignment = Alignment.Center
        ) {
            val index = remember { (image.id.toULong() % 12uL).toInt() }
            Spacer (
                modifier
                    .size(32.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = shapes[index].toShape()
                    )
            )

            AsyncImage(
                model = model,
                onState = { state ->
                    if (state is AsyncImagePainter.State.Success) {
                        state.result.image.toImageBitmap()?.let {
                            onBitmapLoaded(it)
                        }
                    }
                },
                contentDescription = image.name,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(
                            key = "image-${image.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> tween(500) },
                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                        clipInOverlayDuringTransition = OverlayClip(
                            MaterialTheme.shapes.medium
                        )
                    )
                    .clip(MaterialTheme.shapes.medium),
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
