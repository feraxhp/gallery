package com.feraxhp.gallery.components.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.feraxhp.gallery.model.*
import com.feraxhp.gallery.viewmodel.*
import kotlin.math.*

@Composable
fun CanvasViewport(
    imageUri: String,
    uiState: EditUiState,
    onUpdatePreview: (EditOperation?) -> Unit,
    onPhotoBoundsChanged: ((bounds: Rect, aspectRatio: Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val marginPxY = remember(density) { with(density) { 16.dp.toPx() } }
    val marginPxX = remember(density) { with(density) { 24.dp.toPx() } }

    var viewportSize by remember { mutableStateOf(IntSize(800, 600)) }
    val canvasW = max(100f, viewportSize.width.toFloat())
    val canvasH = max(100f, viewportSize.height.toFloat())
    val canvasCenter = remember(canvasW, canvasH) { Offset(canvasW / 2f, canvasH / 2f) }

    // Coil Painter for the image being edited
    val painter = rememberAsyncImagePainter(
        model = imageUri,
        contentScale = ContentScale.Fit
    )
    val painterState by painter.state.collectAsState()

    // Original aspect ratio calculation to prevent stretching
    val imageAspectRatio = remember(painterState, painter.intrinsicSize) {
        val successImage = (painterState as? AsyncImagePainter.State.Success)?.result?.image
        if (successImage != null && successImage.width > 0 && successImage.height > 0) {
            successImage.width.toFloat() / successImage.height.toFloat()
        } else {
            val intrinsicSize = painter.intrinsicSize
            val hasIntrinsicSize = intrinsicSize.width > 0f && intrinsicSize.height > 0f &&
                    !intrinsicSize.width.isNaN() && !intrinsicSize.height.isNaN() &&
                    intrinsicSize.width != Float.POSITIVE_INFINITY && intrinsicSize.height != Float.POSITIVE_INFINITY
            if (hasIntrinsicSize) intrinsicSize.width / intrinsicSize.height else (3f / 4f)
        }
    }

    // Available space inside safe margins
    val safeMarginX = min(marginPxX, canvasW / 4f)
    val safeMarginY = min(marginPxY, canvasH / 4f)
    val availableW = max(64f, canvasW - 2 * safeMarginX)
    val availableH = max(64f, canvasH - 2 * safeMarginY)

    // Fixed selection window target area (~88% of viewport so ~12% margin shows image borders around selection)
    val windowMaxW = availableW * 0.88f
    val windowMaxH = availableH * 0.88f

    // 1. Calculate accumulated transformation state from undoStack and activePreviewOperation
    val accumulatedState = uiState.getAccumulatedState()
    val totalRotation = accumulatedState.totalRotation
    val flipH = accumulatedState.flipH
    val flipV = accumulatedState.flipV
    val straightenAngle = accumulatedState.straightenAngle
    val currentRatio = accumulatedState.ratio
    val opCropRect = accumulatedState.cropRect
    val boundsMode = uiState.boundsMode
    val gridMode = uiState.gridMode

    // Calculate effective displayed dimensions taking 90° / 270° rotation into account
    val isSwapped = (abs(totalRotation.roundToInt()) % 180) != 0
    val effectiveAspectRatio = if (isSwapped) 1f / imageAspectRatio else imageAspectRatio

    // Base un-straightened photo dimensions (independent of straightenAngle so the selection window never shrinks on rotation)
    val effectiveW: Float
    val effectiveH: Float
    if (effectiveAspectRatio > windowMaxW / windowMaxH) {
        effectiveW = windowMaxW
        effectiveH = windowMaxW / effectiveAspectRatio
    } else {
        effectiveH = windowMaxH
        effectiveW = windowMaxH * effectiveAspectRatio
    }

    val imgBaseW = if (isSwapped) effectiveH else effectiveW
    val imgBaseH = if (isSwapped) effectiveW else effectiveH

    // Fixed reference bounds of the un-straightened photo
    val basePhotoBounds = remember(canvasW, canvasH, effectiveW, effectiveH) {
        Rect(
            (canvasW - effectiveW) / 2f,
            (canvasH - effectiveH) / 2f,
            (canvasW + effectiveW) / 2f,
            (canvasH + effectiveH) / 2f
        )
    }

    // Circumscribed outer box of the rotated photo
    val rad = abs(straightenAngle) * (PI.toFloat() / 180f)
    val cosA = cos(rad)
    val sinA = sin(rad)
    val outerW = effectiveW * cosA + effectiveH * sinA
    val outerH = effectiveW * sinA + effectiveH * cosA
    val outerBounds = remember(canvasW, canvasH, outerW, outerH) {
        Rect(
            (canvasW - outerW) / 2f,
            (canvasH - outerH) / 2f,
            (canvasW + outerW) / 2f,
            (canvasH + outerH) / 2f
        )
    }

    val photoBounds = if (boundsMode == BoundsMode.FIT) outerBounds else basePhotoBounds

    LaunchedEffect(basePhotoBounds, effectiveAspectRatio) {
        onPhotoBoundsChanged?.invoke(basePhotoBounds, effectiveAspectRatio)
    }

    // Reconstruct cropRect in basePhotoBounds space, then in BoundsMode.CROP constrain inside rotated photo
    val initialCropRect = remember(opCropRect, basePhotoBounds, outerBounds, boundsMode, straightenAngle, effectiveW, effectiveH) {
        val rawRect = opCropRect?.let { normRect ->
            val rect = if (
                normRect.left >= 0f && normRect.top >= 0f &&
                normRect.right <= 1.001f && normRect.bottom <= 1.001f
            ) {
                normRect.fromNormalized(basePhotoBounds)
            } else {
                normRect
            }
            val boundRef = if (boundsMode == BoundsMode.FIT) outerBounds else basePhotoBounds
            val minW = min(32f, boundRef.width * 0.15f)
            val minH = min(32f, boundRef.height * 0.15f)
            val w = rect.width.coerceIn(minW, boundRef.width)
            val h = rect.height.coerceIn(minH, boundRef.height)
            val left = safeCoerceIn(rect.left, boundRef.left, boundRef.right - w)
            val top = safeCoerceIn(rect.top, boundRef.top, boundRef.bottom - h)
            Rect(left, top, left + w, top + h)
        } ?: if (boundsMode == BoundsMode.FIT) outerBounds else basePhotoBounds

        if (boundsMode == BoundsMode.CROP && abs(straightenAngle) > 0.01f) {
            constrainRectInsideRotatedPhoto(
                rect = rawRect,
                photoCenter = canvasCenter,
                photoW = effectiveW,
                photoH = effectiveH,
                angleDegrees = straightenAngle
            )
        } else {
            rawRect
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val activeToolId = (uiState.state as? EditState.EDITING)?.activeTool?.id
    val isCropToolActive = activeToolId == "crop"

    // Local gesture state
    var activeCropRect by remember { mutableStateOf<Rect?>(null) }
    var dragHandle by remember { mutableStateOf<HandleType?>(null) }
    var isInteractingWithPhoto by remember { mutableStateOf(false) }

    // Clear activeCropRect once ViewModel state / bounds updates and user is not dragging
    LaunchedEffect(uiState.undoStack, uiState.activePreviewOperation, basePhotoBounds, boundsMode, straightenAngle) {
        if (dragHandle == null && !isInteractingWithPhoto) {
            activeCropRect = null
        }
    }

    val currentCropRect = activeCropRect ?: initialCropRect

    // Calculate fixed ~88% viewport selection window for the current crop's aspect ratio:
    // By scaling the photo underneath by (windowW / currentCropRect.width) and centering on currentCropRect.center,
    // the selection box on screen stays at a constant ~88% size during rotation and pinch-to-zoom,
    // while the photo underneath zooms and moves!
    val cropRatio = max(0.05f, currentCropRect.width / max(1f, currentCropRect.height))
    val targetWindowW: Float
    val targetWindowH: Float
    if (cropRatio > windowMaxW / windowMaxH) {
        targetWindowW = windowMaxW
        targetWindowH = windowMaxW / cropRatio
    } else {
        targetWindowH = windowMaxH
        targetWindowW = windowMaxH * cropRatio
    }

    // In Mode 1 (FIT), scale so outerBounds also fits when not zoomed in; in Mode 2 (CROP), always keep the selection window at targetWindowW x targetWindowH!
    val computedScale = if (boundsMode == BoundsMode.CROP) {
        targetWindowW / max(1f, currentCropRect.width)
    } else {
        val fitOuterScale = min(windowMaxW / max(1f, outerW), windowMaxH / max(1f, outerH))
        if (opCropRect == null) fitOuterScale else min(targetWindowW / max(1f, currentCropRect.width), fitOuterScale * 2f)
    }

    // Lock camera transform only while dragging a border resize handle (TOP_LEFT, RIGHT, etc.)
    // so the handle tracks the finger 1:1. When panning/zooming the photo underneath or rotating,
    // do NOT lock so the selection box stays completely fixed in size and centered on screen!
    var lockedHandleScale by remember { mutableStateOf<Float?>(null) }
    var lockedHandleFocus by remember { mutableStateOf<Offset?>(null) }

    val targetScale = lockedHandleScale ?: computedScale
    val targetFocus = lockedHandleFocus ?: if (boundsMode == BoundsMode.CROP || opCropRect != null) {
        currentCropRect.center
    } else {
        canvasCenter
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        label = "viewport_scale"
    )
    val animatedFocus by animateOffsetAsState(
        targetValue = targetFocus,
        label = "viewport_focus"
    )

    // Use immediate values during photo pan/zoom/rotation so there is zero lag between the photo and selection window
    val isLivePhotoTransform = isInteractingWithPhoto || abs(straightenAngle) > 0.001f || boundsMode == BoundsMode.CROP
    val displayScale = when {
        lockedHandleScale != null -> lockedHandleScale!!
        isLivePhotoTransform -> targetScale
        else -> animatedScale
    }
    val displayFocus = when {
        lockedHandleFocus != null -> lockedHandleFocus!!
        isLivePhotoTransform -> targetFocus
        else -> animatedFocus
    }

    // Keep latest state references for pointerInput
    val latestCropRect by rememberUpdatedState(currentCropRect)
    val latestBasePhotoBounds by rememberUpdatedState(basePhotoBounds)
    val latestDragBounds by rememberUpdatedState(photoBounds)
    val latestRatio by rememberUpdatedState(currentRatio)
    val latestBoundsMode by rememberUpdatedState(boundsMode)
    val latestStraightenAngle by rememberUpdatedState(straightenAngle)
    val latestEffectiveW by rememberUpdatedState(effectiveW)
    val latestEffectiveH by rememberUpdatedState(effectiveH)
    val latestCanvasCenter by rememberUpdatedState(canvasCenter)
    val latestDisplayScale by rememberUpdatedState(displayScale)
    val latestDisplayFocus by rememberUpdatedState(displayFocus)
    val latestIsCropToolActive by rememberUpdatedState(isCropToolActive)
    val latestOnUpdatePreview by rememberUpdatedState(onUpdatePreview)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .clipToBounds()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val touchRadiusPx = 28.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startScale = latestDisplayScale.coerceAtLeast(0.05f)
                        val startFocus = latestDisplayFocus
                        val center = latestCanvasCenter

                        // Screen rect of the current selection box
                        val startRect = latestCropRect
                        val screenRect = Rect(
                            left = center.x + (startRect.left - startFocus.x) * startScale,
                            top = center.y + (startRect.top - startFocus.y) * startScale,
                            right = center.x + (startRect.right - startFocus.x) * startScale,
                            bottom = center.y + (startRect.bottom - startFocus.y) * startScale
                        )

                        // Check if the user grabbed one of the 8 border resize handles while in Crop tool
                        val hitHandle = if (latestIsCropToolActive) {
                            getHitHandle(down.position, screenRect, touchRadiusPx)
                        } else {
                            HandleType.CENTER
                        }

                        val isResizeHandle = latestIsCropToolActive &&
                                hitHandle != null &&
                                hitHandle != HandleType.CENTER

                        if (isResizeHandle) {
                            dragHandle = hitHandle
                            lockedHandleScale = startScale
                            lockedHandleFocus = startFocus
                            activeCropRect = startRect
                        } else {
                            // Direct photo pan / pinch-to-zoom underneath the fixed selection box
                            dragHandle = null
                            lockedHandleScale = null
                            lockedHandleFocus = null
                            isInteractingWithPhoto = true
                            activeCropRect = startRect
                        }

                        var moved = false
                        do {
                            val event = awaitPointerEvent()
                            val pointerCount = event.changes.count { it.pressed }

                            if (pointerCount >= 2 && isResizeHandle) {
                                // Switching to 2 fingers transitions into photo pinch-to-zoom
                                dragHandle = null
                                lockedHandleScale = null
                                lockedHandleFocus = null
                                isInteractingWithPhoto = true
                            }

                            val currentRect = activeCropRect ?: latestCropRect
                            val bounds = latestDragBounds

                            if (dragHandle != null && pointerCount == 1) {
                                // Resize selection handle in photo coordinates
                                val pan = event.calculatePan()
                                if (pan != Offset.Zero) {
                                    moved = true
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    val photoDelta = Offset(
                                        x = pan.x / (lockedHandleScale ?: startScale),
                                        y = pan.y / (lockedHandleScale ?: startScale)
                                    )
                                    val updated = updateCropRect(
                                        rect = currentRect,
                                        handle = dragHandle,
                                        delta = photoDelta,
                                        paramMinX = bounds.left,
                                        paramMinY = bounds.top,
                                        paramMaxX = bounds.right,
                                        paramMaxY = bounds.bottom,
                                        ratio = latestRatio
                                    )
                                    activeCropRect = if (latestBoundsMode == BoundsMode.CROP && abs(latestStraightenAngle) > 0.01f) {
                                        constrainRectInsideRotatedPhoto(
                                            rect = updated,
                                            photoCenter = latestCanvasCenter,
                                            photoW = latestEffectiveW,
                                            photoH = latestEffectiveH,
                                            angleDegrees = latestStraightenAngle
                                        )
                                    } else {
                                        updated
                                    }
                                }
                            } else {
                                // Pan and/or pinch-to-zoom the photo underneath the fixed selection window
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    moved = true
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }

                                    val curScale = latestDisplayScale.coerceAtLeast(0.05f)
                                    val curRatio = currentRect.width / max(1f, currentRect.height)

                                    // Zooming the photo UP (zoomChange > 1) reduces the crop window in photo space
                                    val minPhotoW = min(32f, bounds.width * 0.15f)
                                    val maxPhotoW = min(bounds.width, bounds.height * curRatio)
                                    val newW = (currentRect.width / zoomChange).coerceIn(minPhotoW, max(minPhotoW, maxPhotoW))
                                    val newH = newW / curRatio

                                    // Dragging the photo by +panChange moves the crop window on the photo by -panChange / curScale
                                    val rawCx = currentRect.center.x - panChange.x / curScale
                                    val rawCy = currentRect.center.y - panChange.y / curScale

                                    val clampedLeft = safeCoerceIn(rawCx - newW / 2f, bounds.left, bounds.right - newW)
                                    val clampedTop = safeCoerceIn(rawCy - newH / 2f, bounds.top, bounds.bottom - newH)
                                    val candidate = Rect(clampedLeft, clampedTop, clampedLeft + newW, clampedTop + newH)

                                    activeCropRect = if (latestBoundsMode == BoundsMode.CROP && abs(latestStraightenAngle) > 0.01f) {
                                        constrainRectInsideRotatedPhoto(
                                            rect = candidate,
                                            photoCenter = latestCanvasCenter,
                                            photoW = latestEffectiveW,
                                            photoH = latestEffectiveH,
                                            angleDegrees = latestStraightenAngle
                                        )
                                    } else {
                                        candidate
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        val finalRect = activeCropRect
                        val refBounds = latestBasePhotoBounds
                        if (moved && finalRect != null && refBounds.width > 0f && refBounds.height > 0f) {
                            latestOnUpdatePreview(
                                EditOperation.CropOp(
                                    bounds = finalRect.toNormalized(refBounds),
                                    ratio = latestRatio
                                )
                            )
                        }
                        dragHandle = null
                        lockedHandleScale = null
                        lockedHandleFocus = null
                        isInteractingWithPhoto = false
                    }
                }
        ) {
            val isImageLoaded = painterState is AsyncImagePainter.State.Success
            val rect = currentCropRect

            // Map photo-space rectangles to screen space using (displayFocus, displayScale)
            val displayedRect = Rect(
                left = canvasCenter.x + (rect.left - displayFocus.x) * displayScale,
                top = canvasCenter.y + (rect.top - displayFocus.y) * displayScale,
                right = canvasCenter.x + (rect.right - displayFocus.x) * displayScale,
                bottom = canvasCenter.y + (rect.bottom - displayFocus.y) * displayScale
            )
            val displayedOuterBounds = Rect(
                left = canvasCenter.x + (outerBounds.left - displayFocus.x) * displayScale,
                top = canvasCenter.y + (outerBounds.top - displayFocus.y) * displayScale,
                right = canvasCenter.x + (outerBounds.right - displayFocus.x) * displayScale,
                bottom = canvasCenter.y + (outerBounds.bottom - displayFocus.y) * displayScale
            )

            // 1. In Mode 1 (BoundsMode.FIT), when rotated by straightenAngle, draw alpha checkerboard
            // inside the selected crop rect so empty corners clearly show the alpha layer.
            if (boundsMode == BoundsMode.FIT && abs(straightenAngle) > 0.05f) {
                clipRect(
                    left = displayedRect.left,
                    top = displayedRect.top,
                    right = displayedRect.right,
                    bottom = displayedRect.bottom
                ) {
                    drawAlphaCheckerboard(displayedRect)
                }
            }

            // 2. Draw the transformed photo underneath the selection window
            val photoCenterScreenX = canvasCenter.x + (canvasCenter.x - displayFocus.x) * displayScale
            val photoCenterScreenY = canvasCenter.y + (canvasCenter.y - displayFocus.y) * displayScale

            clipRect(
                left = max(0f, displayedOuterBounds.left),
                top = max(0f, displayedOuterBounds.top),
                right = min(size.width, displayedOuterBounds.right),
                bottom = min(size.height, displayedOuterBounds.bottom)
            ) {
                withTransform(
                    transformBlock = {
                        translate(left = photoCenterScreenX, top = photoCenterScreenY)
                        scale(scaleX = displayScale, scaleY = displayScale, pivot = Offset.Zero)
                        rotate(degrees = straightenAngle, pivot = Offset.Zero)
                        scale(
                            scaleX = if (flipH) -1f else 1f,
                            scaleY = if (flipV) -1f else 1f,
                            pivot = Offset.Zero
                        )
                        rotate(degrees = totalRotation, pivot = Offset.Zero)
                        translate(left = -imgBaseW / 2f, top = -imgBaseH / 2f)
                    }
                ) {
                    if (!isImageLoaded && imageUri.isBlank()) {
                        drawPlaceholderScene(imgBaseW, imgBaseH)
                    }
                    with(painter) {
                        draw(size = Size(imgBaseW, imgBaseH))
                    }
                }
            }

            // 3. Dimming overlay on the photo outside the fixed ~88% selection window
            // so the user sees the borders of the image outside the selection!
            val hasOuterMarginVisible =
                displayedOuterBounds.left < displayedRect.left - 0.5f ||
                displayedOuterBounds.top < displayedRect.top - 0.5f ||
                displayedOuterBounds.right > displayedRect.right + 0.5f ||
                displayedOuterBounds.bottom > displayedRect.bottom + 0.5f

            if (hasOuterMarginVisible) {
                drawPath(
                    path = Path().apply {
                        addRect(displayedOuterBounds)
                        addRect(displayedRect)
                        fillType = PathFillType.EvenOdd
                    },
                    color = Color.Black.copy(alpha = 0.58f)
                )
            }

            // 4. Draw Grid inside the selection window
            drawGrid(gridMode, displayedRect)

            // 5. Draw Handles (when Crop tool is active) or clean selection frame
            if (isCropToolActive) {
                drawHandles(displayedRect, primaryColor)
            } else if (hasOuterMarginVisible || activeToolId == "rotate" || boundsMode == BoundsMode.CROP) {
                drawRect(
                    color = primaryColor.copy(alpha = 0.85f),
                    topLeft = displayedRect.topLeft,
                    size = displayedRect.size,
                    style = Stroke(1.5.dp.toPx())
                )
            }
        }
    }
}

/**
 * Ensures [rect] lies completely inside the photo of size ([photoW], [photoH]) centered at [photoCenter]
 * and rotated by [angleDegrees].
 * If all 4 corners of [rect] are already inside the rotated photo, [rect] is returned unchanged.
 */
fun constrainRectInsideRotatedPhoto(
    rect: Rect,
    photoCenter: Offset,
    photoW: Float,
    photoH: Float,
    angleDegrees: Float
): Rect {
    if (photoW <= 1f || photoH <= 1f) return rect

    val rad = angleDegrees * (PI.toFloat() / 180f)
    val cosT = cos(rad)
    val sinT = sin(rad)
    val absCos = abs(cosT)
    val absSin = abs(sinT)

    val photoHW = photoW / 2f
    val photoHH = photoH / 2f

    val hw = rect.width / 2f
    val hh = rect.height / 2f

    // Half-extents of the axis-aligned rect projected onto the rotated photo's local (u, v) axes
    val deltaU = hw * absCos + hh * absSin
    val deltaV = hw * absSin + hh * absCos

    // Rect center relative to photoCenter in canvas (x, y) and rotated photo local (u, v) coordinates
    val dx = rect.center.x - photoCenter.x
    val dy = rect.center.y - photoCenter.y
    var uc = dx * cosT + dy * sinT
    var vc = -dx * sinT + dy * cosT

    // Fast path: all 4 corners are already strictly inside the rotated photo!
    if (abs(uc) + deltaU <= photoHW + 0.5f && abs(vc) + deltaV <= photoHH + 0.5f) {
        return rect
    }

    // First, if the rect size (deltaU, deltaV) fits inside the rotated photo by nudging its center
    // inward without shrinking, clamp (uc, vc) as much as possible.
    if (deltaU <= photoHW && deltaV <= photoHH) {
        uc = uc.coerceIn(-(photoHW - deltaU), photoHW - deltaU)
        vc = vc.coerceIn(-(photoHH - deltaV), photoHH - deltaV)
        val newDx = uc * cosT - vc * sinT
        val newDy = uc * sinT + vc * cosT
        val newCx = photoCenter.x + newDx
        val newCy = photoCenter.y + newDy
        return Rect(newCx - hw, newCy - hh, newCx + hw, newCy + hh)
    }

    // Otherwise, scale (hw, hh) down just enough for the corners to touch the rotated edges
    val maxScaleFromCenter = min(
        photoHW / max(1f, deltaU),
        photoHH / max(1f, deltaV)
    ).coerceIn(0.05f, 1f)

    val scaledHW = hw * maxScaleFromCenter
    val scaledHH = hh * maxScaleFromCenter
    val scaledDeltaU = deltaU * maxScaleFromCenter
    val scaledDeltaV = deltaV * maxScaleFromCenter

    val maxU = max(0f, photoHW - scaledDeltaU)
    val maxV = max(0f, photoHH - scaledDeltaV)
    uc = uc.coerceIn(-maxU, maxU)
    vc = vc.coerceIn(-maxV, maxV)

    val newDx = uc * cosT - vc * sinT
    val newDy = uc * sinT + vc * cosT
    val newCx = photoCenter.x + newDx
    val newCy = photoCenter.y + newDy

    return Rect(newCx - scaledHW, newCy - scaledHH, newCx + scaledHW, newCy + scaledHH)
}

private fun DrawScope.drawAlphaCheckerboard(rect: Rect) {
    val tileSize = 10.dp.toPx().coerceAtLeast(8f)
    val lightColor = Color(0xFF3A3A3C)
    val darkColor = Color(0xFF242426)

    drawRect(color = darkColor, topLeft = rect.topLeft, size = rect.size)

    val cols = ceil(rect.width / tileSize).toInt()
    val rows = ceil(rect.height / tileSize).toInt()

    for (row in 0 until rows) {
        val top = rect.top + row * tileSize
        val h = min(tileSize, rect.bottom - top)
        if (h <= 0f) continue
        for (col in 0 until cols) {
            if ((row + col) % 2 == 0) {
                val left = rect.left + col * tileSize
                val w = min(tileSize, rect.right - left)
                if (w > 0f) {
                    drawRect(
                        color = lightColor,
                        topLeft = Offset(left, top),
                        size = Size(w, h)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPlaceholderScene(imgBaseW: Float, imgBaseH: Float) {
    drawRect(Color(0xFF1E293B), size = Size(imgBaseW, imgBaseH))
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF38BDF8), Color(0xFFF43F5E))
        ),
        size = Size(imgBaseW, imgBaseH * 0.65f)
    )
    drawCircle(
        color = Color(0xFFFDE047),
        radius = min(imgBaseW, imgBaseH) * 0.15f,
        center = Offset(imgBaseW * 0.75f, imgBaseH * 0.25f)
    )
    val mountainPath = Path().apply {
        moveTo(imgBaseW * 0.05f, imgBaseH * 0.65f)
        lineTo(imgBaseW * 0.35f, imgBaseH * 0.25f)
        lineTo(imgBaseW * 0.65f, imgBaseH * 0.65f)
        close()
    }
    drawPath(mountainPath, color = Color(0xFF334155))
    drawRect(
        color = Color(0xFF22C55E),
        topLeft = Offset(0f, imgBaseH * 0.65f),
        size = Size(imgBaseW, imgBaseH * 0.35f)
    )
}

enum class HandleType {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER
}

fun safeCoerceIn(value: Float, minVal: Float, maxVal: Float): Float {
    val low = min(minVal, maxVal)
    val high = max(minVal, maxVal)
    return value.coerceIn(low, high)
}

fun getHitHandle(pos: Offset, rect: Rect, touchRadiusPx: Float): HandleType? {
    val cornerRadius = min(touchRadiusPx, min(rect.width, rect.height) * 0.38f).coerceAtLeast(16f)

    // 1. Check closest corner first
    val corners = listOf(
        HandleType.TOP_LEFT to (pos - rect.topLeft).getDistance(),
        HandleType.TOP_RIGHT to (pos - rect.topRight).getDistance(),
        HandleType.BOTTOM_LEFT to (pos - rect.bottomLeft).getDistance(),
        HandleType.BOTTOM_RIGHT to (pos - rect.bottomRight).getDistance()
    )
    val closestCorner = corners.minByOrNull { it.second }
    if (closestCorner != null && closestCorner.second <= cornerRadius) {
        return closestCorner.first
    }

    // 2. Check mid-edge handles directly around their centers
    val midHandles = listOf(
        HandleType.TOP to (pos - Offset(rect.center.x, rect.top)).getDistance(),
        HandleType.BOTTOM to (pos - Offset(rect.center.x, rect.bottom)).getDistance(),
        HandleType.LEFT to (pos - Offset(rect.left, rect.center.y)).getDistance(),
        HandleType.RIGHT to (pos - Offset(rect.right, rect.center.y)).getDistance()
    )
    val closestMid = midHandles.minByOrNull { it.second }
    if (closestMid != null && closestMid.second <= cornerRadius) {
        return closestMid.first
    }

    // 3. Check edge lines (keeping interior center free for panning)
    val innerEdgeBand = min(touchRadiusPx * 0.65f, min(rect.width, rect.height) * 0.25f)
    if ((pos.y in (rect.top - touchRadiusPx)..(rect.top + innerEdgeBand)) && (pos.x in rect.left..rect.right)) {
        return HandleType.TOP
    }
    if ((pos.y in (rect.bottom - innerEdgeBand)..(rect.bottom + touchRadiusPx)) && (pos.x in rect.left..rect.right)) {
        return HandleType.BOTTOM
    }
    if ((pos.x in (rect.left - touchRadiusPx)..(rect.left + innerEdgeBand)) && (pos.y in rect.top..rect.bottom)) {
        return HandleType.LEFT
    }
    if ((pos.x in (rect.right - innerEdgeBand)..(rect.right + touchRadiusPx)) && (pos.y in rect.top..rect.bottom)) {
        return HandleType.RIGHT
    }

    // 4. Inside the crop rectangle -> pan/move
    if (rect.contains(pos)) return HandleType.CENTER

    return null
}

fun updateCropRect(
    rect: Rect,
    handle: HandleType?,
    delta: Offset,
    paramMinX: Float,
    paramMinY: Float,
    paramMaxX: Float,
    paramMaxY: Float,
    ratio: Float?
): Rect {
    if (handle == null) return rect

    val availW = max(1f, paramMaxX - paramMinX)
    val availH = max(1f, paramMaxY - paramMinY)
    val minSize = min(32f, min(availW, availH) * 0.2f)

    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom

    if (handle == HandleType.CENTER) {
        val w = rect.width.coerceAtMost(availW)
        val h = rect.height.coerceAtMost(availH)
        val maxLeft = max(paramMinX, paramMaxX - w)
        val maxTop = max(paramMinY, paramMaxY - h)
        left = safeCoerceIn(left + delta.x, paramMinX, maxLeft)
        top = safeCoerceIn(top + delta.y, paramMinY, maxTop)
        return Rect(left, top, left + w, top + h)
    }

    if (ratio != null && ratio > 0f) {
        val minW = max(minSize, minSize * ratio).coerceAtMost(min(availW, availH * ratio))
        val minH = (minW / ratio).coerceAtMost(availH)

        when (handle) {
            HandleType.LEFT -> {
                val cy = rect.center.y
                val maxW = min(right - paramMinX, availH * ratio)
                val newW = safeCoerceIn(rect.width - delta.x, minW, maxW)
                val newH = newW / ratio
                left = right - newW
                top = safeCoerceIn(cy - newH / 2f, paramMinY, paramMaxY - newH)
                bottom = top + newH
            }
            HandleType.RIGHT -> {
                val cy = rect.center.y
                val maxW = min(paramMaxX - left, availH * ratio)
                val newW = safeCoerceIn(rect.width + delta.x, minW, maxW)
                val newH = newW / ratio
                right = left + newW
                top = safeCoerceIn(cy - newH / 2f, paramMinY, paramMaxY - newH)
                bottom = top + newH
            }
            HandleType.TOP -> {
                val cx = rect.center.x
                val maxH = min(bottom - paramMinY, availW / ratio)
                val newH = safeCoerceIn(rect.height - delta.y, minH, maxH)
                val newW = newH * ratio
                top = bottom - newH
                left = safeCoerceIn(cx - newW / 2f, paramMinX, paramMaxX - newW)
                right = left + newW
            }
            HandleType.BOTTOM -> {
                val cx = rect.center.x
                val maxH = min(paramMaxY - top, availW / ratio)
                val newH = safeCoerceIn(rect.height + delta.y, minH, maxH)
                val newW = newH * ratio
                bottom = top + newH
                left = safeCoerceIn(cx - newW / 2f, paramMinX, paramMaxX - newW)
                right = left + newW
            }
            HandleType.BOTTOM_RIGHT -> {
                val dx = delta.x
                val dy = delta.y
                val deltaW = if (abs(dx) >= abs(dy)) dx else dy * ratio
                val maxW = min(paramMaxX - left, (paramMaxY - top) * ratio)
                val newW = safeCoerceIn(rect.width + deltaW, minW, maxW)
                val newH = newW / ratio
                right = left + newW
                bottom = top + newH
            }
            HandleType.BOTTOM_LEFT -> {
                val dx = -delta.x
                val dy = delta.y
                val deltaW = if (abs(dx) >= abs(dy)) dx else dy * ratio
                val maxW = min(right - paramMinX, (paramMaxY - top) * ratio)
                val newW = safeCoerceIn(rect.width + deltaW, minW, maxW)
                val newH = newW / ratio
                left = right - newW
                bottom = top + newH
            }
            HandleType.TOP_RIGHT -> {
                val dx = delta.x
                val dy = -delta.y
                val deltaW = if (abs(dx) >= abs(dy)) dx else dy * ratio
                val maxW = min(paramMaxX - left, (bottom - paramMinY) * ratio)
                val newW = safeCoerceIn(rect.width + deltaW, minW, maxW)
                val newH = newW / ratio
                right = left + newW
                top = bottom - newH
            }
            HandleType.TOP_LEFT -> {
                val dx = -delta.x
                val dy = -delta.y
                val deltaW = if (abs(dx) >= abs(dy)) dx else dy * ratio
                val maxW = min(right - paramMinX, (bottom - paramMinY) * ratio)
                val newW = safeCoerceIn(rect.width + deltaW, minW, maxW)
                val newH = newW / ratio
                left = right - newW
                top = bottom - newH
            }
        }
    } else {
        // Free mode: edges and corners move freely in both X and Y directions
        when (handle) {
            HandleType.LEFT -> {
                left = safeCoerceIn(left + delta.x, paramMinX, right - minSize)
            }
            HandleType.RIGHT -> {
                right = safeCoerceIn(right + delta.x, left + minSize, paramMaxX)
            }
            HandleType.TOP -> {
                top = safeCoerceIn(top + delta.y, paramMinY, bottom - minSize)
            }
            HandleType.BOTTOM -> {
                bottom = safeCoerceIn(bottom + delta.y, top + minSize, paramMaxY)
            }
            HandleType.BOTTOM_RIGHT -> {
                right = safeCoerceIn(right + delta.x, left + minSize, paramMaxX)
                bottom = safeCoerceIn(bottom + delta.y, top + minSize, paramMaxY)
            }
            HandleType.BOTTOM_LEFT -> {
                left = safeCoerceIn(left + delta.x, paramMinX, right - minSize)
                bottom = safeCoerceIn(bottom + delta.y, top + minSize, paramMaxY)
            }
            HandleType.TOP_RIGHT -> {
                right = safeCoerceIn(right + delta.x, left + minSize, paramMaxX)
                top = safeCoerceIn(top + delta.y, paramMinY, bottom - minSize)
            }
            HandleType.TOP_LEFT -> {
                left = safeCoerceIn(left + delta.x, paramMinX, right - minSize)
                top = safeCoerceIn(top + delta.y, paramMinY, bottom - minSize)
            }
        }
    }

    left = safeCoerceIn(left, paramMinX, paramMaxX - minSize)
    right = safeCoerceIn(right, left + minSize, paramMaxX)
    top = safeCoerceIn(top, paramMinY, paramMaxY - minSize)
    bottom = safeCoerceIn(bottom, top + minSize, paramMaxY)

    return Rect(left, top, right, bottom)
}

// [CFG-GRID] Mathematical Grid Engine
fun DrawScope.drawGrid(mode: GridMode, rect: Rect) {
    if (mode == GridMode.NONE || rect.width <= 1f || rect.height <= 1f) return

    val linePaint = Color.White.copy(alpha = 0.75f)
    val subLinePaint = Color.White.copy(alpha = 0.45f)
    val strokeW = 1.dp.toPx()

    when (mode) {
        GridMode.RULE_OF_THIRDS -> { // [GRID-THIRDS] Rule of Thirds
            val hStep = rect.width / 3f
            val vStep = rect.height / 3f

            // Vertical lines
            drawLine(linePaint, Offset(rect.left + hStep, rect.top), Offset(rect.left + hStep, rect.bottom), strokeWidth = strokeW)
            drawLine(linePaint, Offset(rect.left + hStep * 2, rect.top), Offset(rect.left + hStep * 2, rect.bottom), strokeWidth = strokeW)

            // Horizontal lines
            drawLine(linePaint, Offset(rect.left, rect.top + vStep), Offset(rect.right, rect.top + vStep), strokeWidth = strokeW)
            drawLine(linePaint, Offset(rect.left, rect.top + vStep * 2), Offset(rect.right, rect.top + vStep * 2), strokeWidth = strokeW)
        }
        GridMode.FOURTHS -> { // [GRID-FOURTHS] Fourths
            val hStep = rect.width / 4f
            val vStep = rect.height / 4f

            for (i in 1..3) {
                drawLine(linePaint, Offset(rect.left + hStep * i, rect.top), Offset(rect.left + hStep * i, rect.bottom), strokeWidth = strokeW)
                drawLine(linePaint, Offset(rect.left, rect.top + vStep * i), Offset(rect.right, rect.top + vStep * i), strokeWidth = strokeW)
            }
        }
        GridMode.GOLDEN_RATIO -> { // [GRID-GOLDEN] Complete Fibonacci / Golden Spiral
            val invPhi = 0.6180339887f // 1 / phi

            var curLeft = rect.left
            var curTop = rect.top
            var curRight = rect.right
            var curBottom = rect.bottom

            val spiralPath = Path()
            val subdivisionsPath = Path()

            for (step in 0 until 9) {
                val curW = curRight - curLeft
                val curH = curBottom - curTop
                if (curW < 2f || curH < 2f) break

                when (step % 4) {
                    0 -> {
                        val splitX = curLeft + curW * invPhi
                        val boxW = splitX - curLeft
                        val boxH = curH
                        subdivisionsPath.moveTo(splitX, curTop)
                        subdivisionsPath.lineTo(splitX, curBottom)
                        spiralPath.arcTo(
                            rect = Rect(curLeft, curTop, curLeft + 2f * boxW, curTop + 2f * boxH),
                            startAngleDegrees = 180f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = (step == 0)
                        )
                        curLeft = splitX
                    }
                    1 -> {
                        val splitY = curTop + curH * invPhi
                        val boxW = curW
                        val boxH = splitY - curTop
                        subdivisionsPath.moveTo(curLeft, splitY)
                        subdivisionsPath.lineTo(curRight, splitY)
                        spiralPath.arcTo(
                            rect = Rect(curRight - 2f * boxW, curTop, curRight, curTop + 2f * boxH),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        curTop = splitY
                    }
                    2 -> {
                        val splitX = curRight - curW * invPhi
                        val boxW = curRight - splitX
                        val boxH = curH
                        subdivisionsPath.moveTo(splitX, curTop)
                        subdivisionsPath.lineTo(splitX, curBottom)
                        spiralPath.arcTo(
                            rect = Rect(curRight - 2f * boxW, curBottom - 2f * boxH, curRight, curBottom),
                            startAngleDegrees = 0f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        curRight = splitX
                    }
                    3 -> {
                        val splitY = curBottom - curH * invPhi
                        val boxW = curW
                        val boxH = curBottom - splitY
                        subdivisionsPath.moveTo(curLeft, splitY)
                        subdivisionsPath.lineTo(curRight, splitY)
                        spiralPath.arcTo(
                            rect = Rect(curLeft, curBottom - 2f * boxH, curLeft + 2f * boxH, curBottom),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 90f,
                            forceMoveTo = false
                        )
                        curBottom = splitY
                    }
                }
            }

            clipRect(rect.left, rect.top, rect.right, rect.bottom) {
                drawPath(subdivisionsPath, subLinePaint, style = Stroke(strokeW))
                drawPath(spiralPath, linePaint, style = Stroke(strokeW * 1.4f, cap = StrokeCap.Round))
            }
        }
    }
}

// [OVR-HANDLES-GEOMETRY] Handles Geometry
fun DrawScope.drawHandles(rect: Rect, color: Color) {
    val handleRadius = 8.dp.toPx()
    val cornerLen = min(22.dp.toPx(), min(rect.width, rect.height) * 0.32f)
    val cornerThickness = 3.5.dp.toPx()

    // Draw outer crop border
    drawRect(color, rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx()))

    val leftX = rect.left
    val rightX = rect.right
    val topY = rect.top
    val bottomY = rect.bottom
    val centerX = rect.center.x
    val centerY = rect.center.y

    // Mid-edges semicircles anchored directly on the crop rect borders
    drawArc(
        color = color,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(leftX - handleRadius, centerY - handleRadius),
        size = Size(handleRadius * 2, handleRadius * 2)
    )
    drawArc(
        color = color,
        startAngle = -90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(rightX - handleRadius, centerY - handleRadius),
        size = Size(handleRadius * 2, handleRadius * 2)
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(centerX - handleRadius, topY - handleRadius),
        size = Size(handleRadius * 2, handleRadius * 2)
    )
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(centerX - handleRadius, bottomY - handleRadius),
        size = Size(handleRadius * 2, handleRadius * 2)
    )

    // Corners L-shapes drawn as continuous mitered paths so corner vertices have no missing notch
    val cornerStyle = Stroke(
        width = cornerThickness,
        cap = StrokeCap.Round,
        join = StrokeJoin.Miter
    )
    val cornersPath = Path().apply {
        // Top-Left
        moveTo(leftX, topY + cornerLen)
        lineTo(leftX, topY)
        lineTo(leftX + cornerLen, topY)

        // Top-Right
        moveTo(rightX - cornerLen, topY)
        lineTo(rightX, topY)
        lineTo(rightX, topY + cornerLen)

        // Bottom-Left
        moveTo(leftX, bottomY - cornerLen)
        lineTo(leftX, bottomY)
        lineTo(leftX + cornerLen, bottomY)

        // Bottom-Right
        moveTo(rightX - cornerLen, bottomY)
        lineTo(rightX, bottomY)
        lineTo(rightX, bottomY - cornerLen)
    }
    drawPath(cornersPath, color = color, style = cornerStyle)
}
