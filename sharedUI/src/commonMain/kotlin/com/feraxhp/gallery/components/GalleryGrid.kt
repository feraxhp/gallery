package com.feraxhp.gallery.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.ShatterData
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

data class DragSelectionInfo(
    val initialSelection: Boolean,
    val affectedIds: MutableSet<Long>
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryGrid(
    images: List<GalleryImage>,
    isLoading: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onImageClick: (GalleryImage, List<GalleryImage>) -> Unit,
    onToggleSelection: (GalleryImage) -> Unit,
    onSetSelection: (GalleryImage, Boolean) -> Unit,
    selectedImageIds: Set<Long>,
    isSelectionMode: Boolean,
    deletedImageIds: Set<Long>,
    isDeletedFromDetail: Boolean,
    onClearDeletedState: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    gridModifier: Modifier = Modifier
) {
    // Offset de la propia galería respecto al root para corregir el posicionamiento de la animación
    var galleryOffset by remember { mutableStateOf(Offset.Zero) }

    // Para rastrear posiciones y bitmaps de las imágenes
    val imageBounds = remember { mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    val imageBitmaps = remember { mutableStateMapOf<Long, ImageBitmap>() }
    
    // Estado para la animación activa
    var activeShatterEffects by remember { mutableStateOf<Map<Long, ShatterData>>(emptyMap()) }
    
    val pullToRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var dragSelectionState by remember { mutableStateOf<DragSelectionInfo?>(null) }
    
    // Estado para la animación de reunión (Gathering)
    val gatheringProgress = remember { Animatable(0f) }
    var gatheringTargetId by remember { mutableStateOf<Long?>(null) }
    var imagesToGather by remember { mutableStateOf<Map<Long, ShatterData>>(emptyMap()) }

    // Usamos rememberUpdatedState para evitar reiniciar el pointerInput cuando cambian las funciones o estados
    val currentImages by rememberUpdatedState(images)
    val currentOnSetSelection by rememberUpdatedState(onSetSelection)
    val currentSelectedImageIds by rememberUpdatedState(selectedImageIds)
    
    LaunchedEffect(deletedImageIds) {
        if (deletedImageIds.isEmpty()) {
            imagesToGather = emptyMap()
            activeShatterEffects = emptyMap()
            gatheringProgress.snapTo(0f)
            return@LaunchedEffect
        }

        val targetId = currentImages.find { it.id in deletedImageIds }?.id
        if (targetId == null) {
            onClearDeletedState()
            return@LaunchedEffect
        }
        
        val gatherMap = mutableMapOf<Long, ShatterData>()
        deletedImageIds.forEach { id ->
            val bounds = imageBounds[id]
            val bitmap = imageBitmaps[id]
            if (bounds != null && bitmap != null) {
                gatherMap[id] = ShatterData(
                    bitmap = bitmap,
                    offset = IntOffset(bounds.left.toInt(), bounds.top.toInt()),
                    size = IntSize(bounds.width.toInt(), bounds.height.toInt())
                )
            }
        }
        
        gatheringTargetId = targetId

        if (gatherMap.isNotEmpty()) {
            try {
                if (isDeletedFromDetail) {
                    // Caso 1: Borrado desde Detail - Sin animación de reunión
                    snapshotFlow { animatedVisibilityScope.transition.currentState == EnterExitState.Visible }
                        .first { it }
                    delay(60.milliseconds)
                    
                    imagesToGather = gatherMap
                    
                    val targetData = gatherMap[targetId]
                    if (targetData != null) {
                        activeShatterEffects = activeShatterEffects + (targetId to targetData)
                    }
                    // Esperamos un poco para asegurar que el shatter sea visible antes de terminar el efecto
                    delay(100.milliseconds)
                } else {
                    // Caso 2: Borrado desde el Grid - Con animación de reunión (Gathering)
                    snapshotFlow { animatedVisibilityScope.transition.targetState == EnterExitState.Visible }
                        .first { it }
                    
                    imagesToGather = gatherMap

                    val targetData = gatherMap[targetId]
                    val maxDistance = if (targetData != null) {
                        gatherMap.values.maxOfOrNull {
                            val dx = (it.offset.x - targetData.offset.x).toFloat()
                            val dy = (it.offset.y - targetData.offset.y).toFloat()
                            kotlin.math.sqrt(dx * dx + dy * dy)
                        } ?: 0f
                    } else 0f
                    
                    val calculatedDuration = ((maxDistance / 1200f) * 1000).toInt().coerceIn(350, 550)

                    gatheringProgress.snapTo(0f)
                    
                    val gatheringJob = launch {
                        gatheringProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = calculatedDuration, easing = FastOutSlowInEasing)
                        )
                    }

                    // Solapamos el inicio del ShatterEffect antes de que termine la reunión
                    delay((calculatedDuration * 0.80f).toInt().milliseconds)

                    if (targetData != null) {
                        activeShatterEffects = activeShatterEffects + (targetId to targetData)
                    }
                    
                    gatheringJob.join()
                }
            } finally {
                // Limpiar estado de reunión local al terminar o si se cancela (Undo)
                imagesToGather = emptyMap()
                gatheringProgress.snapTo(0f)
            }
        }
    }

    val groupedImages = remember(images) {
        images.groupBy {
            Instant.fromEpochSeconds(it.dateAdded)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date
        }
    }

    if (isLoading && images.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else {
        Box(
            modifier = modifier
                .onGloballyPositioned { galleryOffset = it.positionInRoot() }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (dragSelectionState != null && event.changes.any { it.changedToUp() }) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val rootOffset = galleryOffset + offset
                            val imageId = imageBounds.entries.find { it.value.contains(rootOffset) }?.key
                            val image = currentImages.find { it.id == imageId }
                            if (image != null) {
                                val isCurrentlySelected = image.id in currentSelectedImageIds
                                dragSelectionState = DragSelectionInfo(!isCurrentlySelected, mutableSetOf(image.id))
                                currentOnSetSelection(image, !isCurrentlySelected)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDrag = { change, _ ->
                            val rootOffset = galleryOffset + change.position
                            val imageId = imageBounds.entries.find { it.value.contains(rootOffset) }?.key
                            val image = currentImages.find { it.id == imageId }
                            val state = dragSelectionState
                            if (image != null && state != null && image.id !in state.affectedIds) {
                                currentOnSetSelection(image, state.initialSelection)
                                state.affectedIds.add(image.id)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onDragEnd = { dragSelectionState = null },
                        onDragCancel = { dragSelectionState = null }
                    )
                }
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = topPadding)
                            .zIndex(10f)
                    )
                }
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = gridModifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 4.dp,
                        top = 4.dp + topPadding,
                        end = 4.dp,
                        bottom = 100.dp + WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding()
                    ),
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
                            val isGathering = image.id in imagesToGather
                            val isDeleted = image.id in deletedImageIds
                            GalleryImageTile(
                                image = image,
                                allImages = images,
                                onImageClick = onImageClick,
                                onToggleSelection = onToggleSelection,
                                isSelected = image.id in selectedImageIds,
                                isSelectionMode = isSelectionMode,
                                onPositioned = { rect ->
                                    imageBounds[image.id] = rect
                                },
                                onBitmapLoaded = { bitmap ->
                                    imageBitmaps[image.id] = bitmap
                                },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                modifier = Modifier
                                    .animateItem()
                                    .alpha(if (isGathering || isDeleted) 0f else 1f)
                            )
                        }
                    }
                }
            }
            
            // Capa de efectos visuales (Reunión y Shatter)
            Box(Modifier.fillMaxSize()) {
                // 1. Animación de reunión (Gathering)
                if (gatheringProgress.value > 0f) {
                    val targetData = imagesToGather[gatheringTargetId]
                    if (targetData != null) {
                        val density = androidx.compose.ui.platform.LocalDensity.current
                        imagesToGather.forEach { (id, data) ->
                            val isTarget = id == gatheringTargetId
                            val progress = gatheringProgress.value
                            
                            // Posición relativa e interpolada
                            val currentX = (data.offset.x - galleryOffset.x) + 
                                ((targetData.offset.x - data.offset.x) * progress)
                            val currentY = (data.offset.y - galleryOffset.y) + 
                                ((targetData.offset.y - data.offset.y) * progress)
                            
                            val scale = 1f
                            val alpha = if (isTarget) 1f else 1f - progress
                            
                            if (alpha > 0f) {
                                Image(
                                    bitmap = data.bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(currentX.toInt(), currentY.toInt())
                                        }
                                        .size(
                                            width = with(density) { (data.size.width * scale).toDp() },
                                            height = with(density) { (data.size.height * scale).toDp() }
                                        )
                                        .alpha(alpha)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                // 2. Efecto Shatter (Rotura)
                activeShatterEffects.forEach { (id, data) ->
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    key(id) {
                        ShatterEffect(
                            bitmap = data.bitmap,
                            size = data.size,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        (data.offset.x - galleryOffset.x).toInt(),
                                        (data.offset.y - galleryOffset.y).toInt()
                                    )
                                }
                                .size(
                                    width = with(density) { data.size.width.toDp() },
                                    height = with(density) { data.size.height.toDp() }
                                ),
                            onAnimationEnd = {
                                activeShatterEffects = activeShatterEffects - id
                            }
                        )
                    }
                }
            }
        }
    }
}
