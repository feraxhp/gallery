package com.feraxhp.gallery.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onLayoutRectChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.exp
import kotlin.math.roundToInt
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
    gridModifier: Modifier = Modifier,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (Int, Int) -> Unit = { _, _ -> },
    onImageLongClick: ((GalleryImage, List<GalleryImage>) -> Unit)? = null,
    enableMultiSelectDrag: Boolean = true
) {
    var savedScrollIndex by rememberSaveable { mutableIntStateOf(initialScrollIndex) }
    var savedScrollOffset by rememberSaveable { mutableIntStateOf(initialScrollOffset) }
    var hasRestoredInitialScroll by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = savedScrollIndex,
        initialFirstVisibleItemScrollOffset = savedScrollOffset
    )

    LaunchedEffect(images.isNotEmpty()) {
        if (images.isNotEmpty() && !hasRestoredInitialScroll) {
            hasRestoredInitialScroll = true
            val targetIdx = if (savedScrollIndex > 0) savedScrollIndex else initialScrollIndex
            val targetOff = if (savedScrollIndex > 0) savedScrollOffset else initialScrollOffset
            if (targetIdx > 0 && gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0) {
                gridState.scrollToItem(targetIdx, targetOff)
            }
        }
    }

    LaunchedEffect(gridState, images.isNotEmpty()) {
        if (images.isNotEmpty()) {
            snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (hasRestoredInitialScroll) {
                        savedScrollIndex = index
                        savedScrollOffset = offset
                        onScrollChanged(index, offset)
                    }
                }
        }
    }

    // Offset de la propia galería respecto al root para corregir el posicionamiento de la animación
    var galleryOffset by remember { mutableStateOf(Offset.Zero) }

    // Mapas no observables para rastrear posiciones y bitmaps sin disparar Snapshots en cada frame de scroll
    val imageBounds = remember { mutableMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    val imageBitmaps = remember { mutableMapOf<Long, ImageBitmap>() }
    val fallbackShapes = rememberGalleryShapes()
    val density = LocalDensity.current
    
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
    val currentDeletedImageIds by rememberUpdatedState(deletedImageIds)

    LaunchedEffect(deletedImageIds) {
        if (deletedImageIds.isEmpty()) {
            imagesToGather = emptyMap()
            // Solo limpiamos los efectos de shatter si la imagen ha vuelto a la galería (Undo).
            // Si la imagen ya no está en 'images', dejamos que el ShatterEffect termine su animación naturalmente.
            val currentVisibleIds = currentImages.map { it.id }.toSet()
            activeShatterEffects = activeShatterEffects.filterKeys { it !in currentVisibleIds }
            
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
                    snapshotFlow { animatedVisibilityScope.transition.currentState == EnterExitState.Visible }
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
        val timeZone = TimeZone.currentSystemDefault()
        images.groupBy {
            Instant.fromEpochSeconds(it.dateAdded)
                .toLocalDateTime(timeZone)
                .date
        }
    }

    val itemDates = remember(groupedImages) {
        buildList {
            groupedImages.forEach { (date, imagesInDate) ->
                add(date)
                repeat(imagesInDate.size) {
                    add(date)
                }
            }
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
                .onLayoutRectChanged(throttleMillis = 64, debounceMillis = 64) { layoutBounds ->
                    val pos = layoutBounds.positionInRoot
                    galleryOffset = Offset(pos.x.toFloat(), pos.y.toFloat())
                }
                .pointerInput(enableMultiSelectDrag) {
                    if (!enableMultiSelectDrag) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (dragSelectionState != null && event.changes.any { it.changedToUp() }) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(enableMultiSelectDrag) {
                    if (!enableMultiSelectDrag) return@pointerInput
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
                    state = gridState,
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
                        item(
                            key = "header_${date}",
                            contentType = "header",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
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
                        items(
                            items = imagesInDate,
                            key = { it.id },
                            contentType = { "image" }
                        ) { image ->
                            val isGathering = image.id in imagesToGather
                            val isDeleted = image.id in deletedImageIds
                            val shapeIdx = (image.id.toULong() % 12uL).toInt()
                            GalleryImageTile(
                                image = image,
                                allImages = images,
                                onImageClick = onImageClick,
                                onToggleSelection = onToggleSelection,
                                isSelected = image.id in selectedImageIds,
                                isSelectionMode = isSelectionMode,
                                fallbackShape = fallbackShapes[shapeIdx],
                                onImageLongClick = onImageLongClick,
                                onPositioned = { rect ->
                                    imageBounds[image.id] = rect
                                },
                                onBitmapLoaded = { bitmap ->
                                    imageBitmaps[image.id] = bitmap
                                },
                                onDisposeTile = { id ->
                                    if (id !in currentDeletedImageIds && id !in activeShatterEffects) {
                                        imageBounds.remove(id)
                                        imageBitmaps.remove(id)
                                    }
                                },
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                modifier = Modifier
                                    .animateItem()
                                    .graphicsLayer {
                                        alpha = if (isGathering || isDeleted) 0f else 1f
                                    }
                            )
                        }
                    }
                }
            }

            // Fast Scroller con forma de gota de distribución normal y píldora de fecha
            if (itemDates.size > 12) {
                GaussianFastScroller(
                    gridState = gridState,
                    itemDates = itemDates,
                    topPadding = topPadding,
                    bottomPadding = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .zIndex(5f)
                )
            }
            
            // Capa de efectos visuales (Reunión y Shatter)
            Box(Modifier.fillMaxSize()) {
                // 1. Animación de reunión (Gathering)
                if (gatheringProgress.value > 0f) {
                    val targetData = imagesToGather[gatheringTargetId]
                    if (targetData != null) {
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
                                imageBounds.remove(id)
                                imageBitmaps.remove(id)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpanishDate(date: LocalDate): String {
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
    return "${date.day} de $monthName de ${date.year}"
}

@Composable
private fun GaussianFastScroller(
    gridState: LazyGridState,
    itemDates: List<LocalDate>,
    topPadding: Dp,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var isRecentlyScrolled by remember { mutableStateOf(false) }

    val isScrollInProgress by remember {
        derivedStateOf { gridState.isScrollInProgress }
    }

    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            isRecentlyScrolled = true
        } else {
            delay(1500.milliseconds)
            if (!gridState.isScrollInProgress && !isDragging) {
                isRecentlyScrolled = false
            }
        }
    }

    val scrollFraction by remember(itemDates.size) {
        derivedStateOf {
            val totalItems = itemDates.size
            if (totalItems <= 1) {
                0f
            } else {
                val visibleCount = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val maxScrollIndex = (totalItems - visibleCount).coerceAtLeast(1)
                (gridState.firstVisibleItemIndex.toFloat() / maxScrollIndex.toFloat()).coerceIn(0f, 1f)
            }
        }
    }

    val activeFraction = if (isDragging) dragFraction else scrollFraction

    val currentDateText by remember(itemDates) {
        derivedStateOf {
            if (itemDates.isEmpty()) ""
            else {
                val idx = if (isDragging) {
                    (dragFraction * (itemDates.size - 1)).roundToInt().coerceIn(0, itemDates.lastIndex)
                } else {
                    gridState.firstVisibleItemIndex.coerceIn(0, itemDates.lastIndex)
                }
                formatSpanishDate(itemDates[idx])
            }
        }
    }

    val dropletHeightDp = 96.dp
    val dropletWidthDp = 36.dp
    val dropletHeightPx = with(density) { dropletHeightDp.toPx() }

    val peakScale by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.78f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dropletPeakScale"
    )

    val dropletColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .padding(top = topPadding + 12.dp, bottom = bottomPadding)
            .width(260.dp)
            .onSizeChanged { trackHeightPx = it.height }
    ) {
        val usableTrackPx = (trackHeightPx - dropletHeightPx).coerceAtLeast(1f)
        val currentThumbY = (activeFraction * usableTrackPx).roundToInt()

        AnimatedVisibility(
            visible = isRecentlyScrolled || isDragging,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(220)) { it },
            exit = fadeOut(tween(300)) + slideOutHorizontally(tween(250)) { it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, currentThumbY) }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                AnimatedVisibility(
                    visible = isDragging && currentDateText.isNotEmpty(),
                    enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.85f),
                    exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.85f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 6.dp,
                        tonalElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = currentDateText,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                // Gota con forma de curva de distribución normal (Gaussiana) pegada al borde derecho
                Box(
                    modifier = Modifier
                        .size(width = dropletWidthDp, height = dropletHeightDp)
                        .pointerInput(itemDates.size, usableTrackPx) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    isDragging = true
                                    dragFraction = scrollFraction
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val deltaFraction = dragAmount / usableTrackPx
                                    val newFraction = (dragFraction + deltaFraction).coerceIn(0f, 1f)
                                    dragFraction = newFraction
                                    val targetIndex = (newFraction * (itemDates.size - 1))
                                        .roundToInt()
                                        .coerceIn(0, (itemDates.size - 1).coerceAtLeast(0))
                                    scope.launch {
                                        gridState.scrollToItem(targetIndex)
                                    }
                                },
                                onDragEnd = { isDragging = false },
                                onDragCancel = { isDragging = false }
                            )
                        }
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val mu = h / 2f
                            val sigma = h / 5.2f
                            val maxRadius = w * peakScale
                            val edgeT = (h / 2f) / sigma
                            val baseBell = exp(-0.5f * edgeT * edgeT)
                            val normFactor = (1f - baseBell).coerceAtLeast(0.001f)

                            val path = Path().apply {
                                moveTo(w, 0f)
                                val steps = 36
                                for (i in 0..steps) {
                                    val y = (i.toFloat() / steps) * h
                                    val t = (y - mu) / sigma
                                    val bell = ((exp(-0.5f * t * t) - baseBell) / normFactor).coerceIn(0f, 1f)
                                    val x = w - maxRadius * bell
                                    lineTo(x, y)
                                }
                                lineTo(w, h)
                                close()
                            }

                            onDrawBehind {
                                drawPath(
                                    path = path,
                                    color = dropletColor
                                )
                            }
                        },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "Desplazamiento rápido",
                        tint = iconTint,
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(20.dp)
                    )
                }
            }
        }
    }
}
