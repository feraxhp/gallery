package com.feraxhp.gallery.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.util.rememberThumbnailModel
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

private val SelectionBadgeShape = MixedCornerShape(6.dp, 8.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberGalleryShapes(): List<Shape> {
    val s0 = shapes[0].toShape()
    val s1 = shapes[1].toShape()
    val s2 = shapes[2].toShape()
    val s3 = shapes[3].toShape()
    val s4 = shapes[4].toShape()
    val s5 = shapes[5].toShape()
    val s6 = shapes[6].toShape()
    val s7 = shapes[7].toShape()
    val s8 = shapes[8].toShape()
    val s9 = shapes[9].toShape()
    val s10 = shapes[10].toShape()
    val s11 = shapes[11].toShape()
    return remember(s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11) {
        listOf(s0, s1, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11)
    }
}

enum class Type {
    LOADED,
    ERROR,
    LOADING,
}


@Suppress("RememberReturnType")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GalleryImageTile(
    image: GalleryImage,
    allImages: List<GalleryImage>,
    onImageClick: (GalleryImage, List<GalleryImage>) -> Unit,
    onToggleSelection: (GalleryImage) -> Unit,
    onPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    onBitmapLoaded: (ImageBitmap) -> Unit,
    onDisposeTile: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    fallbackShape: Shape? = null,
) {
    val haptic = LocalHapticFeedback.current
    val index = remember(image.id) { (image.id.toULong() % 12uL).toInt() }
    val resolvedFallbackShape = fallbackShape ?: shapes[index].toShape()
    var state by remember(image.id) { mutableStateOf(Type.LOADING) }
    var isVisibleInWindow by remember { mutableStateOf(false) }
    val model = rememberThumbnailModel(
        uri = image.uri,
        isVideo = image.type == MediaType.VIDEO,
        duration = image.duration,
        sizePx = 360
    )

    androidx.compose.runtime.DisposableEffect(image.id) {
        onDispose {
            onDisposeTile(image.id)
        }
    }

    val isTransitionActive = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
    val attachSharedBounds = isVisibleInWindow || isTransitionActive

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .onVisibilityChanged(minFractionVisible = 0.001f) { visible ->
                    isVisibleInWindow = visible
                }
                .onLayoutRectChanged(throttleMillis = 64, debounceMillis = 64) { layoutBounds ->
                    val b = layoutBounds.boundsInRoot
                    onPositioned(
                        Rect(
                            left = b.left.toFloat(),
                            top = b.top.toFloat(),
                            right = b.right.toFloat(),
                            bottom = b.bottom.toFloat()
                        )
                    )
                }
                .clip(MaterialTheme.shapes.medium)
                .background(
                    when (state) {
                        Type.LOADING,
                        Type.LOADED -> {
                            if (isSelected) { MaterialTheme.colorScheme.inverseSurface }
                            else { MaterialTheme.colorScheme.surfaceContainerHigh }
                        }
                        Type.ERROR -> { MaterialTheme.colorScheme.errorContainer }
                    }
                )
                .clickable {
                    if (isSelectionMode) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleSelection(image)
                    } else {
                        onImageClick(image, allImages)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if(state != Type.LOADED) {
                Spacer (
                    Modifier
                        .size(32.dp)
                        .background(
                            color = when (state) {
                                Type.LOADING -> { MaterialTheme.colorScheme.primary }
                                Type.LOADED,
                                Type.ERROR -> { MaterialTheme.colorScheme.onError }
                            },
                            shape = resolvedFallbackShape
                        )
                )
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .zIndex(2f)
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .size(30.dp)
                        .background(
                            when (state) {
                                Type.LOADING,
                                Type.LOADED -> {
                                    if (isSelected) { MaterialTheme.colorScheme.inverseSurface }
                                    else { MaterialTheme.colorScheme.surfaceContainerHigh }
                                }
                                Type.ERROR -> { MaterialTheme.colorScheme.onError }
                            },
                            SelectionBadgeShape
                        ),
                    contentAlignment = Alignment.TopStart
                ) {
                    Spacer (
                        Modifier
                            .padding(3.dp)
                            .size(16.dp)
                            .background(
                                color = when (state) {
                                    Type.LOADED,
                                    Type.LOADING -> {
                                        if (isSelected) { MaterialTheme.colorScheme.inversePrimary }
                                        else { MaterialTheme.colorScheme.onSurface }
                                    }
                                    Type.ERROR -> { MaterialTheme.colorScheme.onError }
                                },
                                shape = resolvedFallbackShape
                            )
                    )
                }
            }

            val targetShape = if (isSelected) MaterialTheme.shapes.small else MaterialTheme.shapes.medium

            AsyncImage(
                model = model,
                onState = { imState ->
                    if (imState is AsyncImagePainter.State.Success) {
                        state = Type.LOADED
                        imState.result.image.toImageBitmap()?.let {
                            onBitmapLoaded(it)
                        }
                    } else if (imState is AsyncImagePainter.State.Error) {
                        state = Type.ERROR
                    }
                },
                contentDescription = image.name,
                modifier = Modifier
                    .padding(if (isSelected) 4.dp else 0.dp)
                    .fillMaxSize()
                    .then(
                        if (attachSharedBounds) {
                            Modifier.sharedBounds(
                                sharedContentState = rememberSharedContentState(
                                    key = "image-${image.id}"
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> tween(500) },
                                resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                clipInOverlayDuringTransition = OverlayClip(targetShape)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(targetShape),
                contentScale = ContentScale.Crop
            )


            Text(
                text = "${image.albumName}",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                ),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .widthIn(max = 64.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape
                    )
                    .padding(2.dp)
                    .padding(horizontal = 4.dp)
                ,
            )


            if (image.type == MediaType.VIDEO) {
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
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Video",
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
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

class MixedCornerShape(
    private val invertedRadius: Dp,
    private val normalRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val invertedPx = with(density) { invertedRadius.toPx() }
        val normalPx = with(density) { normalRadius.toPx() }

        // Map your base dimensions subtracting the protruding inverted cuts
        val baseWidth = size.width - invertedPx
        val baseHeight = size.height - invertedPx

        val path = Path().apply {
            // 1. Line from (0, 0) to (width + invertedRadius, 0)
            moveTo(0f, 0f)
            lineTo(size.width, 0f)

            // 2. Arc from (width + invertedRadius, 0) to (width, invertedRadius)
            arcTo(
                rect = Rect(
                    left = size.width - invertedPx,
                    top = 0f,
                    right = size.width + invertedPx,
                    bottom = invertedPx * 2
                ),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )

            // Right edge line joining to step 3
            lineTo(baseWidth, baseHeight - normalPx)

            // 3. Normal bottom-right rounding to (width - normalRadius, height)
            arcTo(
                rect = Rect(
                    left = baseWidth - (normalPx * 2),
                    top = baseHeight - (normalPx * 2),
                    right = baseWidth,
                    bottom = baseHeight
                ),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 90f,
                forceMoveTo = false
            )

            // 4. Line from step 3 to (invertedRadius, height)
            lineTo(invertedPx, baseHeight)

            // 5. Arc from (invertedRadius, height) to (0, height + invertedRadius)
            arcTo(
                rect = Rect(
                    left = 0f,
                    top = baseHeight,
                    right = invertedPx * 2,
                    bottom = baseHeight + (invertedPx * 2)
                ),
                startAngleDegrees = 270f,
                sweepAngleDegrees = -90f,
                forceMoveTo = false
            )

            // Path to (0,0) and close
            lineTo(0f, 0f)
            close()
        }

        return Outline.Generic(path)
    }
}
