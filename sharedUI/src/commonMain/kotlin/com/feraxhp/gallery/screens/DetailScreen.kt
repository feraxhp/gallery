package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.feraxhp.gallery.components.MapPreview
import com.feraxhp.gallery.components.MetadataItem
import com.feraxhp.gallery.components.MetadataSection
import com.feraxhp.gallery.components.VideoPlayer
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.rememberFullImageModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    images: List<GalleryImage>,
    initialIndex: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    repository: ImageRepository,
    onImageChange: (GalleryImage) -> Unit = {},
    onLoadMetadata: (GalleryImage) -> Unit = {},
    isMetadataLoading: Boolean = false,
    selectedImageId: Long? = null,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onUseAs: () -> Unit = {},
    onMoveToAlbum: () -> Unit,
    onDelete: () -> Unit,
    onRenameImage: suspend (GalleryImage, String) -> GalleryImage? = { _, _ -> null },
    onPickConfirm: ((GalleryImage) -> Unit)? = null,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val safeInitialIndex = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitialIndex) { images.size }
    var showBottomSheet by rememberSaveable { mutableStateOf(false) }
    var isUiVisible by rememberSaveable { mutableStateOf(true) }
    var lastSyncedListSize by remember { mutableIntStateOf(images.size) }
    var isProgrammaticScroll by remember { mutableStateOf(false) }

    LaunchedEffect(selectedImageId, images.size) {
        if (images.isNotEmpty()) {
            val targetId = selectedImageId ?: images.getOrNull(safeInitialIndex)?.id
            val targetIndex = if (targetId != null) images.indexOfFirst { it.id == targetId } else safeInitialIndex
            if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
                isProgrammaticScroll = true
                try {
                    if (lastSyncedListSize <= 1 && images.size > 1) {
                        pagerState.scrollToPage(targetIndex)
                    } else {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                } finally {
                    isProgrammaticScroll = false
                }
            }
            lastSyncedListSize = images.size
        }
    }

    LaunchedEffect(pagerState.currentPage, images, isProgrammaticScroll) {
        if (!isProgrammaticScroll && images.isNotEmpty() && pagerState.currentPage in images.indices) {
            if (lastSyncedListSize <= 1 && images.size > 1 && selectedImageId != null && images[pagerState.currentPage].id != selectedImageId) {
                return@LaunchedEffect
            }
            val currentImage = images[pagerState.currentPage]
            onImageChange(currentImage)
            onLoadMetadata(currentImage)
        }
    }

    val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState

    // Almacenamos las escalas de cada página para saber si podemos hacer scroll
    val pageScales = remember { mutableStateMapOf<Int, Float>() }
    val userScrollEnabled by remember {
        derivedStateOf {
            val currentScale = pageScales[pagerState.currentPage] ?: 1f
            currentScale <= 1.05f
        }
    }
    
    // El offset vertical de la página actual para el efecto de transparencia al cerrar
    val pageOffsetsY = remember { mutableStateMapOf<Int, Float>() }
    val currentOffsetY by remember {
        derivedStateOf {
            pageOffsetsY[pagerState.currentPage] ?: 0f
        }
    }

    // El fondo se vuelve transparente si el targetState ya no es Visible (gesto iniciado)
    val isVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible
    val baseAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = if (isVisible) tween(300) else tween(
            durationMillis = 0,
            delayMillis = 0,
            easing = EaseOutExpo,
        ),
        label = "baseAlpha"
    )

    val backgroundAlpha = (baseAlpha * (1f - (currentOffsetY / 600f).coerceIn(0f, 1f)))

    val showTopBar = isVisible && isUiVisible && !isTransitionRunning && userScrollEnabled && currentOffsetY < 10f

    Scaffold(
        containerColor = Color.Black.copy(alpha = backgroundAlpha),
        topBar = {
            AnimatedVisibility(
                visible = showTopBar,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut()
            ) {
                TopAppBar(
                    modifier = Modifier.pointerInput(Unit) {
                        var accumulatedDragY = 0f
                        detectVerticalDragGestures(
                            onDragStart = { accumulatedDragY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                accumulatedDragY += dragAmount
                                if (accumulatedDragY < -15f || dragAmount < -10f) {
                                    change.consume()
                                    isUiVisible = false
                                }
                            }
                        )
                    },
                    title = { Text("Detalle") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        if (onPickConfirm != null) {
                            FilledTonalButton(
                                onClick = {
                                    val activeImage = images.getOrNull(pagerState.currentPage)
                                    if (activeImage != null) {
                                        onPickConfirm(activeImage)
                                    }
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Seleccionar",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Seleccionar")
                            }
                        } else {
                            val activeImage = images.getOrNull(pagerState.currentPage)
                            if (activeImage?.type == MediaType.IMAGE) {
                                IconButton(onClick = onUseAs) {
                                    Icon(Icons.Default.Wallpaper, contentDescription = "Usar como...")
                                }
                            }
                            IconButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar")
                            }
                            IconButton(onClick = onShare) {
                                Icon(Icons.Default.Share, contentDescription = "Compartir")
                            }
                            IconButton(onClick = onMoveToAlbum) {
                                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Mover")
                            }
                            IconButton(onClick = onDelete) {
                                Icon(Icons.Default.Delete, contentDescription = "Borrar")
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentAlignment = Alignment.Center
            ) {
                if (images.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = userScrollEnabled,
                        key = { page -> images.getOrNull(page)?.id ?: page.toLong() },
                        pageSpacing = 16.dp
                    ) { page ->
                        val pageImage = images.getOrNull(page) ?: return@HorizontalPager
                        val isCurrentPage = page == pagerState.currentPage
                        val isSharedElement = isCurrentPage

                        DetailImageItem(
                            image = pageImage,
                            sharedTransitionScope = this@with,
                            animatedVisibilityScope = animatedVisibilityScope,
                            isSharedElement = isSharedElement,
                            isCurrentPage = isCurrentPage,
                            isVisible = isVisible,
                            onBack = onBack,
                            onToggleUi = { isUiVisible = !isUiVisible },
                            onSetUiVisible = { isUiVisible = it },
                            onScaleChanged = { scale ->
                                pageScales[page] = scale
                            },
                            onOffsetYChanged = { offset ->
                                pageOffsetsY[page] = offset
                            },
                            onSwipeUp = {
                                showBottomSheet = true
                            }
                        )
                    }
                }

                if (showBottomSheet) {
                    val activeImage = images.getOrNull(pagerState.currentPage)
                    if (activeImage != null) {
                        MetadataBottomSheet(
                            image = activeImage,
                            repository = repository,
                            isMetadataLoading = isMetadataLoading,
                            onRenameImage = onRenameImage,
                            onDismiss = { showBottomSheet = false }
                        )
                    }
                }
            }
        }
    }
}

private fun clampZoomOffsets(
    scaleVal: Float,
    rawOffsetX: Float,
    rawOffsetY: Float,
    viewportW: Float,
    viewportH: Float,
    aspectRatio: Float
): Offset {
    if (viewportW <= 0f || viewportH <= 0f || scaleVal <= 1.001f) {
        return Offset.Zero
    }
    val effectiveAspect = if (aspectRatio > 0f) aspectRatio else (viewportW / viewportH)
    val viewportAspect = viewportW / viewportH
    val displayedW: Float
    val displayedH: Float
    if (viewportAspect > effectiveAspect) {
        displayedH = viewportH
        displayedW = viewportH * effectiveAspect
    } else {
        displayedW = viewportW
        displayedH = viewportW / effectiveAspect
    }
    val scaledW = displayedW * scaleVal
    val scaledH = displayedH * scaleVal
    val maxOffsetX = ((scaledW - viewportW) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((scaledH - viewportH) / 2f).coerceAtLeast(0f)
    return Offset(
        x = rawOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
        y = rawOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailImageItem(
    image: GalleryImage,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isSharedElement: Boolean,
    isCurrentPage: Boolean,
    isVisible: Boolean,
    onBack: () -> Unit,
    onToggleUi: () -> Unit,
    onSetUiVisible: (Boolean) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onOffsetYChanged: (Float) -> Unit,
    onSwipeUp: () -> Unit
) {
    val model = rememberFullImageModel(
        uri = image.uri,
        isVideo = image.type == MediaType.VIDEO,
        duration = image.duration,
        thumbnailSizePx = 360
    )

    var backCalled by remember { mutableStateOf(false) }
    val safeOnBack = {
        if (!backCalled) {
            backCalled = true
            onBack()
        }
    }

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var gestureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var loadedAspectRatio by remember(image.uri, image.width, image.height) {
        mutableFloatStateOf(if (image.width > 0 && image.height > 0) image.aspectRatio else 0f)
    }

    val effectiveAspect = if (loadedAspectRatio > 0f) {
        loadedAspectRatio
    } else if (image.width > 0 && image.height > 0) {
        image.aspectRatio
    } else {
        0f
    }

    var videoActive by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(0L) }

    val toggleVideoPlayback = {
        if (!videoActive) {
            videoActive = true
            isPlaying = true
            onSetUiVisible(false)
        } else {
            val nextPlaying = !isPlaying
            isPlaying = nextPlaying
            onSetUiVisible(!nextPlaying)
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            if (videoActive || isPlaying) {
                videoActive = false
                isPlaying = false
                currentTime = 0L
                onSetUiVisible(true)
            }
            if (scale.value != 1f || offsetX.value != 0f || offsetY.value != 0f) {
                gestureJob?.cancel()
                scale.snapTo(1f)
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            }
        }
    }

    LaunchedEffect(scale.value) {
        onScaleChanged(scale.value)
    }

    LaunchedEffect(offsetY.value) {
        onOffsetYChanged(offsetY.value)
    }

    val cornerRadius by animateDpAsState(
        targetValue = if (isVisible) 0.dp else 12.dp,
        label = "cornerRadius"
    )

    val isCentered by remember {
        derivedStateOf { scale.value <= 1.01f && abs(offsetX.value) < 1f && abs(offsetY.value) < 1f }
    }
    var showIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(isCentered) {
        if (isCentered) {
            delay(150.milliseconds)
            showIndicator = false
        } else {
            showIndicator = true
        }
    }

    val showAspectRatio by remember(isSharedElement) {
        derivedStateOf {
            val isTransitionActive = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState
            (isSharedElement && isTransitionActive) || scale.value <= 1.01f
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(image.id) {
                    awaitEachGesture {
                        var isSwipingVertical = false
                        var isSwipingHorizontal = false
                        var isZooming = false
                        var totalPan = Offset.Zero
                        var accumulatedSwipeY = 0f
                        val touchSlop = viewConfiguration.touchSlop

                        awaitFirstDown(requireUnconsumed = false)
                        var currentScale = scale.value
                        var currentOffsetX = offsetX.value
                        var currentOffsetY = offsetY.value

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) break

                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()

                            if (pressedCount > 1 || currentScale > 1.01f || zoomChange != 1f || isZooming) {
                                isZooming = true

                                val newScale = (currentScale * zoomChange).coerceIn(1f, 600f)
                                val actualZoomChange = if (currentScale > 0f) newScale / currentScale else 1f

                                val rawTargetX: Float
                                val rawTargetY: Float

                                if (newScale <= 1.001f) {
                                    rawTargetX = 0f
                                    rawTargetY = 0f
                                } else if (actualZoomChange < 1f && currentScale > 1.001f) {
                                    // Al reducir el zoom hacia 1x, interpolamos progresivamente la posición hacia el centro (0, 0)
                                    val scaleProgress = ((newScale - 1f) / (currentScale - 1f)).coerceIn(0f, 1f)
                                    rawTargetX = currentOffsetX * scaleProgress + panChange.x
                                    rawTargetY = currentOffsetY * scaleProgress + panChange.y
                                } else {
                                    var zoomOffsetX = 0f
                                    var zoomOffsetY = 0f
                                    if (centroid != Offset.Unspecified) {
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val centroidFromCenter = centroid - center
                                        zoomOffsetX = (centroidFromCenter.x - currentOffsetX) * (1f - actualZoomChange)
                                        zoomOffsetY = (centroidFromCenter.y - currentOffsetY) * (1f - actualZoomChange)
                                    }
                                    rawTargetX = currentOffsetX + zoomOffsetX + panChange.x
                                    rawTargetY = currentOffsetY + zoomOffsetY + panChange.y
                                }

                                val clamped = clampZoomOffsets(
                                    scaleVal = newScale,
                                    rawOffsetX = rawTargetX,
                                    rawOffsetY = rawTargetY,
                                    viewportW = size.width.toFloat(),
                                    viewportH = size.height.toFloat(),
                                    aspectRatio = effectiveAspect
                                )

                                currentScale = newScale
                                currentOffsetX = clamped.x
                                currentOffsetY = clamped.y

                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }

                                gestureJob?.cancel()
                                gestureJob = scope.launch {
                                    scale.snapTo(currentScale)
                                    offsetX.snapTo(currentOffsetX)
                                    offsetY.snapTo(currentOffsetY)
                                }
                            } else if (!isSwipingHorizontal) {
                                totalPan += panChange
                                if (!isSwipingVertical) {
                                    if (abs(totalPan.x) > touchSlop && abs(totalPan.x) > abs(totalPan.y)) {
                                        isSwipingHorizontal = true
                                    } else if (abs(totalPan.y) > touchSlop && abs(totalPan.y) > abs(totalPan.x)) {
                                        isSwipingVertical = true
                                    }
                                }

                                if (isSwipingVertical) {
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                    accumulatedSwipeY += panChange.y
                                    currentOffsetY = (currentOffsetY + panChange.y).coerceAtLeast(0f)
                                    gestureJob?.cancel()
                                    gestureJob = scope.launch {
                                        offsetY.snapTo(currentOffsetY)
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (isZooming) {
                            if (currentScale <= 1.05f) {
                                gestureJob?.cancel()
                                gestureJob = scope.launch {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                }
                            } else {
                                val clamped = clampZoomOffsets(
                                    scaleVal = currentScale,
                                    rawOffsetX = currentOffsetX,
                                    rawOffsetY = currentOffsetY,
                                    viewportW = size.width.toFloat(),
                                    viewportH = size.height.toFloat(),
                                    aspectRatio = effectiveAspect
                                )
                                gestureJob?.cancel()
                                gestureJob = scope.launch {
                                    launch { offsetX.animateTo(clamped.x) }
                                    launch { offsetY.animateTo(clamped.y) }
                                }
                            }
                        } else if (currentScale <= 1.01f) {
                            if (currentOffsetY > 200f) {
                                safeOnBack()
                            } else if (accumulatedSwipeY < -120f) {
                                onSwipeUp()
                                gestureJob?.cancel()
                                gestureJob = scope.launch {
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                }
                            } else if (currentOffsetY != 0f || currentOffsetX != 0f) {
                                gestureJob?.cancel()
                                gestureJob = scope.launch {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetY.animateTo(0f) }
                                    launch { offsetX.animateTo(0f) }
                                }
                            }
                        }
                    }
                }
                .pointerInput(image.id) {
                    detectTapGestures(
                        onTap = {
                            if (isVisible) {
                                if (image.type == MediaType.VIDEO || image.isMotionPhoto) {
                                    toggleVideoPlayback()
                                } else {
                                    onToggleUi()
                                }
                            }
                        },
                        onDoubleTap = { tapOffset ->
                            gestureJob?.cancel()
                            gestureJob = scope.launch {
                                if (scale.value > 1.03f || abs(offsetX.value) > 1f || abs(offsetY.value) > 1f) {
                                    launch { scale.animateTo(1f) }
                                    launch { offsetX.animateTo(0f) }
                                    launch { offsetY.animateTo(0f) }
                                } else {
                                    val targetScale = 3f
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val tapFromCenter = tapOffset - center
                                    val rawTargetX = (tapFromCenter.x - offsetX.value) * (1f - targetScale)
                                    val rawTargetY = (tapFromCenter.y - offsetY.value) * (1f - targetScale)
                                    val clamped = clampZoomOffsets(
                                        scaleVal = targetScale,
                                        rawOffsetX = rawTargetX,
                                        rawOffsetY = rawTargetY,
                                        viewportW = size.width.toFloat(),
                                        viewportH = size.height.toFloat(),
                                        aspectRatio = effectiveAspect
                                    )

                                    launch { scale.animateTo(targetScale) }
                                    launch { offsetX.animateTo(clamped.x) }
                                    launch { offsetY.animateTo(clamped.y) }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale.value,
                        scaleY = scale.value,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (showAspectRatio && effectiveAspect > 0f) {
                                Modifier
                                    .wrapContentSize(Alignment.Center)
                                    .aspectRatio(effectiveAspect)
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                ) {
                    AsyncImage(
                        model = model,
                        contentDescription = image.name,
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                val intrinsic = state.painter.intrinsicSize
                                if (intrinsic.width > 0f && intrinsic.height > 0f) {
                                    loadedAspectRatio = intrinsic.width / intrinsic.height
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isSharedElement) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "image-${image.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = { _, _ -> tween(500) },
                                        resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                                        clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerRadius))
                                    )
                                } else Modifier
                            ),
                        contentScale = if (showAspectRatio && effectiveAspect > 0f) ContentScale.Crop else ContentScale.Fit
                    )

                    AnimatedVisibility(
                        visible = videoActive && isVisible,
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(200))
                    ) {
                        VideoPlayer(
                            uri = image.uri,
                            isMotionPhoto = image.isMotionPhoto,
                            isPlaying = isPlaying,
                            modifier = Modifier.fillMaxSize(),
                            onProgressUpdate = { currentTime = it },
                            onVideoClick = { toggleVideoPlayback() }
                        )
                    }
                }
            }

            // Píldoras de UI - Posición fija debajo del espacio del TopAppBar sin moverse al ocultar/mostrar
            val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState

            AnimatedVisibility(
                visible = (image.type == MediaType.VIDEO || image.isMotionPhoto) && isVisible && !isTransitionRunning,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = TopAppBarDefaults.TopAppBarExpandedHeight + 12.dp, start = 16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { toggleVideoPlayback() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        if (videoActive) {
                            Text(
                                text = formatDuration(currentTime),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }

                    if (image.isMotionPhoto && !isPlaying) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable { toggleVideoPlayback() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Motion Photo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showIndicator,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 16.dp)
            ) {
                Surface(
                    onClick = {
                        gestureJob?.cancel()
                        gestureJob = scope.launch {
                            launch { scale.animateTo(1f) }
                            launch { offsetX.animateTo(0f) }
                            launch { offsetY.animateTo(0f) }
                        }
                    },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = CircleShape,
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = "${(scale.value * 10).toInt() / 10.0}x",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetadataBottomSheet(
    image: GalleryImage,
    repository: ImageRepository,
    isMetadataLoading: Boolean = false,
    onRenameImage: suspend (GalleryImage, String) -> GalleryImage? = { _, _ -> null },
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var isRenaming by remember(image.id) { mutableStateOf(false) }
    var renameBaseText by remember(image.id, image.name) {
        mutableStateOf(image.name.substringBeforeLast('.', image.name))
    }
    var isSavingRename by remember { mutableStateOf(false) }
    val extension = remember(image.name) { image.name.substringAfterLast('.', "") }
    
    val copyToClipboard = { text: String? ->
        if (text != null) {
            clipboardManager.setText(AnnotatedString(text))
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 48.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detalles",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (isMetadataLoading) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // --- ETAPA 1: TIEMPO Y UBICACIÓN ---
            
            // Sección de Tiempo
            MetadataSection(title = "Fecha y hora") {
                val timestamp = if (image.dateTaken != null && image.dateTaken > 0) {
                    image.dateTaken
                } else if (image.dateAdded > 0) {
                    image.dateAdded * 1000
                } else {
                    null
                }

                val dateTime = timestamp?.let {
                    Instant.fromEpochMilliseconds(it)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                }

                MetadataItem(
                    icon = Icons.Default.Schedule,
                    label = "Capturada el",
                    value = dateTime?.let {
                        "${it.day}/${it.month.ordinal + 1}/${it.year} ${it.hour}:${it.minute.toString().padStart(2, '0')}"
                    },
                    errorText = "Fecha desconocida",
                    onIconClick = {
                        copyToClipboard(dateTime?.let {
                            "${it.day}/${it.month.ordinal + 1}/${it.year} ${it.hour}:${it.minute.toString().padStart(2, '0')}"
                        })
                    }
                )
            }

            // Sección de Ubicación
            if (image.latitude != null && image.longitude != null) {
                MetadataSection(title = "Ubicación") {
                    val uriHandler = LocalUriHandler.current
                    MapPreview(
                        latitude = image.latitude,
                        longitude = image.longitude,
                        onClick = {
                            uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=${image.latitude},${image.longitude}")
                        }
                    )
                    Text(
                        text = "Ver en Google Maps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=${image.latitude},${image.longitude}")
                            }
                            .padding(top = 4.dp)
                    )
                }
            } else {
                MetadataSection(title = "Ubicación") {
                    MetadataItem(
                        icon = Icons.Default.LocationOn,
                        label = "Lugar",
                        value = null,
                        errorText = "Sin ubicación",
                        onIconClick = { copyToClipboard("Sin ubicación") }
                    )
                }
            }

            // --- ETAPA 2: ARCHIVO Y CÁMARA ---

            // Sección de Archivo
            MetadataSection(title = "Archivo") {
                MetadataItem(
                    icon = Icons.Default.Description,
                    label = "Nombre (mantén presionado para renombrar)",
                    value = image.name,
                    onIconClick = { copyToClipboard(image.name) },
                    onValueLongClick = {
                        renameBaseText = image.name.substringBeforeLast('.', image.name)
                        isRenaming = true
                    },
                    customValueContent = if (isRenaming) {
                        {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedTextField(
                                    value = renameBaseText,
                                    onValueChange = { renameBaseText = it },
                                    singleLine = true,
                                    enabled = !isSavingRename,
                                    modifier = Modifier.weight(1f),
                                    suffix = if (extension.isNotEmpty()) {
                                        { Text(".$extension") }
                                    } else null
                                )
                                IconButton(
                                    enabled = !isSavingRename && renameBaseText.isNotBlank(),
                                    onClick = {
                                        val trimmed = renameBaseText.trim()
                                        if (trimmed.isNotEmpty()) {
                                            scope.launch {
                                                isSavingRename = true
                                                try {
                                                    onRenameImage(image, trimmed)
                                                    isRenaming = false
                                                } finally {
                                                    isSavingRename = false
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Guardar nombre",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    enabled = !isSavingRename,
                                    onClick = { isRenaming = false }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancelar",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else null
                )
                MetadataItem(
                    icon = Icons.Default.Folder,
                    label = "Ruta completa",
                    value = image.path,
                    errorText = "Ruta no disponible",
                    onIconClick = { copyToClipboard(image.path) },
                    onValueClick = { image.path?.let { repository.openInFileManager(it) } }
                )
                MetadataItem(
                    icon = Icons.Default.PhotoAlbum,
                    label = "Álbum",
                    value = image.albumName,
                    errorText = "Álbum desconocido",
                    onIconClick = { copyToClipboard(image.albumName) }
                )
                val resolution = if (image.width > 0) "${image.width} x ${image.height} (${(image.width * image.height / 1000000.0).toString().take(3)} MP)" else null
                MetadataItem(
                    icon = Icons.Default.AspectRatio,
                    label = "Resolución",
                    value = resolution,
                    errorText = "Resolución desconocida",
                    onIconClick = { copyToClipboard(resolution) }
                )
                val fileSize = formatFileSize(image.size)
                MetadataItem(
                    icon = Icons.Default.SdStorage,
                    label = "Tamaño",
                    value = fileSize,
                    onIconClick = { copyToClipboard(fileSize) }
                )
            }

            // Sección de Cámara
            val hasCameraInfo = image.cameraModel != null || image.cameraManufacturer != null ||
                    image.iso != null || image.aperture != null || image.shutterSpeed != null || image.focalLength != null

            if (hasCameraInfo) {
                MetadataSection(title = "Cámara") {
                    val cameraInfo = listOfNotNull(image.cameraManufacturer, image.cameraModel)
                        .joinToString(" ")
                        .trim()
                    
                    MetadataItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Dispositivo",
                        value = if (cameraInfo.isNotEmpty()) cameraInfo else null,
                        errorText = "Cámara desconocida",
                        onIconClick = { copyToClipboard(if (cameraInfo.isNotEmpty()) cameraInfo else null) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            val aperture = image.aperture?.let { "f/$it" }
                            MetadataItem(
                                icon = Icons.Default.SettingsApplications,
                                label = "Apertura",
                                value = aperture,
                                errorText = "-",
                                onIconClick = { copyToClipboard(aperture) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            val exposure = image.shutterSpeed?.let { 
                                if (it.toDoubleOrNull() != null) {
                                    val speed = it.toDouble()
                                    if (speed < 1.0) "1/${(1.0/speed).toInt()}s" else "${speed}s"
                                } else it
                            }
                            MetadataItem(
                                icon = Icons.Default.Timer,
                                label = "Exposición",
                                value = exposure,
                                errorText = "-",
                                onIconClick = { copyToClipboard(exposure) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MetadataItem(
                                icon = Icons.Default.Iso,
                                label = "ISO",
                                value = image.iso,
                                errorText = "-",
                                onIconClick = { copyToClipboard(image.iso) }
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            val focalLength = image.focalLength?.let { 
                                if (it.contains("/")) {
                                    val parts = it.split("/")
                                    if (parts.size == 2) {
                                        (parts[0].toDouble() / parts[1].toDouble()).toString().take(4) + " mm"
                                    } else it
                                } else "$it mm"
                            }
                            MetadataItem(
                                icon = Icons.Default.FilterCenterFocus,
                                label = "Distancia focal",
                                value = focalLength,
                                errorText = "-",
                                onIconClick = { copyToClipboard(focalLength) }
                            )
                        }
                    }
                    
                    if (image.software != null) {
                        MetadataItem(
                            icon = Icons.Default.AutoFixHigh,
                            label = "Software",
                            value = image.software,
                            onIconClick = { copyToClipboard(image.software) }
                        )
                    }
                }
            }
        }
    }
}



private fun formatFileSize(size: Long?): String {
    if (size == null || size <= 0) return "Desconocido"
    return when {
        size >= 1024 * 1024 * 1024 -> "${(size / (1024.0 * 1024.0 * 1024.0)).toString().take(4)} GB"
        size >= 1024 * 1024 -> "${(size / (1024.0 * 1024.0)).toString().take(4)} MB"
        size >= 1024 -> "${(size / 1024.0).toString().take(4)} KB"
        else -> "$size B"
    }
}

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return if (minutes > 0) {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    } else {
        "0:${seconds.toString().padStart(2, '0')}"
    }
}
