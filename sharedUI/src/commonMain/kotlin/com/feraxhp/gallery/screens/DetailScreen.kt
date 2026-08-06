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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.feraxhp.gallery.components.MapPreview
import com.feraxhp.gallery.components.VideoPlayer
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.rememberVideoModel
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
    onBack: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { images.size }
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        onImageChange(images[pagerState.currentPage])
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

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .padding(top = topPadding),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = userScrollEnabled,
                key = { images[it].id },
                pageSpacing = 16.dp
            ) { page ->
                val isSharedElement = if (isVisible) {
                    if (isTransitionRunning) page == initialIndex else page == pagerState.currentPage
                } else {
                    page == pagerState.currentPage
                }

                DetailImageItem(
                    image = images[page],
                    sharedTransitionScope = this@with,
                    animatedVisibilityScope = animatedVisibilityScope,
                    isSharedElement = isSharedElement,
                    isVisible = isVisible,
                    onBack = onBack,
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

            if (showBottomSheet) {
                MetadataBottomSheet(
                    image = images[pagerState.currentPage],
                    repository = repository,
                    onDismiss = { showBottomSheet = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailImageItem(
    image: GalleryImage,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isSharedElement: Boolean,
    isVisible: Boolean,
    onBack: () -> Unit,
    onScaleChanged: (Float) -> Unit,
    onOffsetYChanged: (Float) -> Unit,
    onSwipeUp: () -> Unit
) {
    val model = if (image.type == MediaType.VIDEO) {
        rememberVideoModel(image.uri, image.duration)
    } else {
        image.uri
    }

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

    var videoActive by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isSharedElement) {
        if (!isSharedElement) {
            videoActive = false
            isPlaying = false
            currentTime = 0L
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
            modifier = Modifier.fillMaxSize(),
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
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var isSwipingVertical = false
                            var isZooming = false

                            awaitFirstDown(requireUnconsumed = false)
                            var accumulatedSwipeY = 0f
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                val currentScale = scale.value
                                if (currentScale > 1.01f || zoomChange != 1f || isZooming) {
                                    isZooming = true
                                    event.changes.forEach { it.consume() }

                                    val newScale = (currentScale * zoomChange).coerceIn(1f, 5f)
                                    // El multiplicador escala con el zoom para que el paneo no se sienta lento en aumentos grandes
                                    val panSensitivity = 1.2f * currentScale
                                    scope.launch {
                                        scale.snapTo(newScale)
                                        offsetX.snapTo(offsetX.value + panChange.x * panSensitivity)
                                        offsetY.snapTo(offsetY.value + panChange.y * panSensitivity)
                                    }
                                } else {
                                    if (!isSwipingVertical && abs(panChange.y) > abs(panChange.x) && abs(panChange.y) > 2f) {
                                        isSwipingVertical = true
                                    }

                                    if (isSwipingVertical) {
                                        event.changes.forEach { it.consume() }
                                        accumulatedSwipeY += panChange.y
                                        scope.launch {
                                            offsetY.snapTo((offsetY.value + panChange.y).coerceAtLeast(0f))
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (scale.value <= 1.01f) {
                                if (offsetY.value > 200f) {
                                    safeOnBack()
                                } else if (accumulatedSwipeY < -150f) {
                                    onSwipeUp()
                                } else {
                                    scope.launch {
                                        offsetY.animateTo(0f)
                                        offsetX.animateTo(0f)
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (isVisible) {
                                    if (image.type == MediaType.VIDEO || image.isMotionPhoto) {
                                        if (!videoActive) {
                                            videoActive = true
                                            isPlaying = true
                                        } else {
                                            isPlaying = !isPlaying
                                        }
                                    } else if (scale.value <= 1.01f) {
                                        safeOnBack()
                                    }
                                }
                            },
                            onDoubleTap = {
                                scope.launch {
                                    if (scale.value > 1.01f) {
                                        launch { scale.animateTo(1f) }
                                        launch { offsetX.animateTo(0f) }
                                        launch { offsetY.animateTo(0f) }
                                    } else {
                                        launch { scale.animateTo(3f) }
                                        launch { offsetX.animateTo(0f) }
                                        launch { offsetY.animateTo(0f) }
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
                        .then(
                            if (showAspectRatio) {
                                Modifier
                                    .wrapContentSize(Alignment.Center)
                                    .aspectRatio(image.aspectRatio)
                            } else {
                                Modifier
                            }
                        )
                        .clip(RoundedCornerShape(cornerRadius))
                ) {
                    AsyncImage(
                        model = model,
                        contentDescription = image.name,
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
                        contentScale = if (showAspectRatio) ContentScale.Crop else ContentScale.Fit
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
                            onVideoClick = { isPlaying = !isPlaying }
                        )
                    }
                }
            }

            // Píldoras de UI - Posición fija por encima del objeto con zoom
            val isTransitionRunning = animatedVisibilityScope.transition.currentState != animatedVisibilityScope.transition.targetState

            AnimatedVisibility(
                visible = (image.type == MediaType.VIDEO || image.isMotionPhoto) && isVisible && !isTransitionRunning,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
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
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
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
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val clipboardManager = LocalClipboardManager.current
    
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
            Text(
                text = "Detalles",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
                    label = "Nombre",
                    value = image.name,
                    onIconClick = { copyToClipboard(image.name) }
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

@Composable
private fun MetadataSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun MetadataItem(
    icon: ImageVector,
    label: String,
    value: String?,
    errorText: String = "Desconocido",
    onIconClick: (() -> Unit)? = null,
    onValueClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = CircleShape,
            modifier = Modifier
                .size(40.dp)
                .then(if (onIconClick != null) Modifier.clickable { onIconClick() } else Modifier)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(start = 16.dp)
                .then(if (onValueClick != null) Modifier.clickable { onValueClick() } else Modifier)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
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
