package com.feraxhp.gallery.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.feraxhp.gallery.model.GalleryImage
import com.feraxhp.gallery.model.MediaType
import com.feraxhp.gallery.util.rememberVideoModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun DetailScreen(
    image: GalleryImage,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit
) {
    val model = if (image.type == MediaType.VIDEO) {
        rememberVideoModel(image.uri, image.duration)
    } else {
        image.uri
    }

    // El fondo se vuelve transparente si el targetState ya no es Visible (gesto iniciado)
    val isVisible = animatedVisibilityScope.transition.targetState == EnterExitState.Visible
    val baseAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = if (isVisible) tween(300) else tween(0) // Instantáneo al salir
    )

    var scale by remember { mutableStateOf(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // El alpha también depende de cuánto hayamos deslizado hacia abajo
    val backgroundAlpha = (baseAlpha * (1f - (offsetY.value / 600f).coerceIn(0f, 1f)))

    val isCentered by remember {
        derivedStateOf { scale == 1f && offsetX.value == 0f && offsetY.value == 0f }
    }
    var showIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(isCentered) {
        if (isCentered) {
            delay(500)
            showIndicator = false
        } else {
            showIndicator = true
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .clickable(enabled = scale == 1f) { onBack() }
        ) {
            AsyncImage(
                model = model,
                contentDescription = image.name,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = "image-${image.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                scope.launch {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offsetX.animateTo(0f)
                                        offsetY.animateTo(0f)
                                    } else {
                                        scale = 3f
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)

                            scope.launch {
                                if (scale > 1f || zoom != 1f) {
                                    // Si hay zoom o estamos haciendo el gesto de zoom, movemos la imagen libremente
                                    offsetX.snapTo(offsetX.value + pan.x)
                                    offsetY.snapTo(offsetY.value + pan.y)
                                } else if (scale == 1f && pan.y != 0f) {
                                    // Gesto de deslizar hacia abajo para cerrar (solo si scale es 1 y es un pan vertical)
                                    val newY = (offsetY.value + pan.y).coerceAtLeast(0f)
                                    offsetY.snapTo(newY)
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // Detectamos cuando el usuario suelta la pantalla para decidir si cerrar o resetear
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.all { !it.pressed }) {
                                    // Todos los dedos levantados
                                    if (scale == 1f) {
                                        if (offsetY.value > 200f) {
                                            onBack()
                                        } else {
                                            scope.launch {
                                                offsetY.animateTo(0f)
                                                offsetX.animateTo(0f)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX.value,
                        translationY = offsetY.value
                    ),
                contentScale = ContentScale.Fit
            )

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
                        text = "${(scale * 10).toInt() / 10.0}x",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
