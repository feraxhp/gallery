package com.feraxhp.gallery.components.editor

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.feraxhp.gallery.model.Category
import com.feraxhp.gallery.model.EditOperation
import com.feraxhp.gallery.model.Tool
import com.feraxhp.gallery.viewmodel.EditState
import com.feraxhp.gallery.viewmodel.EditUiState
import com.feraxhp.gallery.viewmodel.fromNormalized
import com.feraxhp.gallery.viewmodel.getAccumulatedState
import com.feraxhp.gallery.viewmodel.toNormalized
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun BottomActionMenu(
    editState: EditState,
    uiState: EditUiState,
    categories: List<Category>,
    imageAspectRatio: Float = 1f,
    photoBounds: Rect = Rect.Zero,
    onCategorySelected: (Category) -> Unit,
    onToolSelected: (Tool) -> Unit,
    onUpdatePreview: (EditOperation?) -> Unit,
    onLongPressItem: (String, String, Any?) -> Unit, // title, description, exampleModel
    modifier: Modifier = Modifier
) {
    val activeTool = (editState as? EditState.EDITING)?.activeTool

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Fixed-height slot for Micro-adjustments so photo never shifts and only tool options animate
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = activeTool,
                contentKey = { it?.id },
                transitionSpec = {
                    (fadeIn() + slideInVertically { height -> height / 2 })
                        .togetherWith(fadeOut() + slideOutVertically { height -> height / 2 })
                },
                label = "micro_adjustments_animation"
            ) { tool ->
                if (tool != null) {
                    MicroAdjustmentsLayer(
                        activeTool = tool,
                        uiState = uiState,
                        imageAspectRatio = imageAspectRatio,
                        photoBounds = photoBounds,
                        onUpdatePreview = onUpdatePreview
                    )
                } else {
                    Spacer(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main bottom bar: only animates when switching between IDLE (categories) and EDITING (category tools)
        AnimatedContent(
            targetState = editState,
            contentKey = { state -> (state as? EditState.EDITING)?.activeCategory?.id ?: "idle" },
            transitionSpec = {
                (slideInVertically { height -> height } + fadeIn())
                    .togetherWith(slideOutVertically { height -> height } + fadeOut())
            },
            label = "bottom_menu_animation"
        ) { state ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomCenter
            ) {
                when (state) {
                    is EditState.IDLE -> {
                        CategoriesRow(
                            categories = categories,
                            onCategorySelected = onCategorySelected,
                            onLongPress = onLongPressItem,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(vertical = 12.dp)
                                .fillMaxWidth()
                        )
                    }
                    is EditState.EDITING -> {
                        ToolsRow(
                            tools = state.activeCategory.tools,
                            activeTool = state.activeTool,
                            onToolSelected = onToolSelected,
                            onLongPress = onLongPressItem,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    RoundedCornerShape(24.dp)
                                )
                                .padding(vertical = 12.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoriesRow(
    categories: List<Category>,
    onCategorySelected: (Category) -> Unit,
    onLongPress: (String, String, Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        items(categories) { category ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { onCategorySelected(category) },
                        onLongClick = { onLongPress(category.name, "Herramientas de ${category.name}", null) }
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.name,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolsRow(
    tools: List<Tool>,
    activeTool: Tool?,
    onToolSelected: (Tool) -> Unit,
    onLongPress: (String, String, Any?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        contentPadding = PaddingValues(horizontal = 24.dp)
    ) {
        items(tools) { tool ->
            val isSelected = activeTool?.id == tool.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                        onClick = { onToolSelected(tool) },
                        onLongClick = { onLongPress(tool.name, tool.explanation, tool.exampleModel) }
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 40.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.name,
                        modifier = Modifier.size(26.dp),
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else LocalContentColor.current
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
        }
    }
}

@Composable
private fun MicroAdjustmentsLayer(
    activeTool: Tool?,
    uiState: EditUiState,
    imageAspectRatio: Float,
    photoBounds: Rect,
    onUpdatePreview: (EditOperation?) -> Unit
) {
    val accumulated = uiState.getAccumulatedState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center
    ) {
        when (activeTool?.id) {
            "crop" -> {
                val currentRatio = accumulated.ratio
                val currentCropRect = accumulated.cropRect?.let { normRect ->
                    if (normRect.left >= 0f && normRect.top >= 0f && normRect.right <= 1.001f && normRect.bottom <= 1.001f) {
                        normRect.fromNormalized(photoBounds)
                    } else {
                        normRect
                    }
                } ?: photoBounds

                data class RatioOption(val label: String, val ratio: Float?)
                val ratioOptions = remember(imageAspectRatio) {
                    listOf(
                        RatioOption("Libre", null),
                        RatioOption("Original", imageAspectRatio),
                        RatioOption("1:1", 1.0f),
                        RatioOption("4:3", 4f / 3f),
                        RatioOption("3:4", 3f / 4f),
                        RatioOption("16:9", 16f / 9f),
                        RatioOption("9:16", 9f / 16f)
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ratioOptions) { option ->
                        val isSelected = when {
                            option.ratio == null && currentRatio == null -> true
                            option.ratio != null && currentRatio != null -> abs(option.ratio - currentRatio) < 0.01f
                            else -> false
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (option.ratio == null) {
                                    onUpdatePreview(EditOperation.CropOp(currentCropRect.toNormalized(photoBounds), null))
                                } else {
                                    val newRect = calculateAspectCropRect(currentCropRect, photoBounds, option.ratio)
                                    onUpdatePreview(EditOperation.CropOp(newRect.toNormalized(photoBounds), option.ratio))
                                }
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
            }
            "rotate" -> {
                val currentAngle = accumulated.straightenAngle
                RotationDialRow(
                    currentAngle = currentAngle,
                    onAngleChange = { angle ->
                        onUpdatePreview(EditOperation.StraightenOp(angle))
                    },
                    onRotateStep = { stepDegrees ->
                        onUpdatePreview(EditOperation.RotateStepOp(stepDegrees))
                    }
                )
            }
            "flip" -> {
                val currentFlipH = accumulated.flipH
                val currentFlipV = accumulated.flipV
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = currentFlipH,
                        onClick = {
                            onUpdatePreview(EditOperation.FlipOp(flipH = true, flipV = false))
                        },
                        label = { Text("Flip Horizontal") }
                    )
                    FilterChip(
                        selected = currentFlipV,
                        onClick = {
                            onUpdatePreview(EditOperation.FlipOp(flipH = false, flipV = true))
                        },
                        label = { Text("Flip Vertical") }
                    )
                }
            }
        }
    }
}

@Composable
private fun RotationDialRow(
    currentAngle: Float,
    onAngleChange: (Float) -> Unit,
    onRotateStep: (Float) -> Unit
) {
    val latestAngle by rememberUpdatedState(currentAngle)
    val latestOnAngleChange by rememberUpdatedState(onAngleChange)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val subTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Degree pill badge on top (tap to reset to 0°)
        Surface(
            shape = CircleShape,
            color = if (abs(currentAngle) > 0.05f) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            },
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onAngleChange(0f) }
        ) {
            Text(
                text = "${currentAngle.roundToInt()}°",
                style = MaterialTheme.typography.labelMedium,
                color = if (abs(currentAngle) > 0.05f) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Perfectly aligned horizontal row: [-90° button] + [tick slider] + [+90° button]
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -90° Step Button
            FilledTonalIconButton(
                onClick = { onRotateStep(-90f) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateLeft,
                    contentDescription = "Rotar -90°",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Interactive tick-mark dial
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val degreesPerPx = 90f / size.width.coerceAtLeast(1).toFloat()
                            val nextAngle = (latestAngle - dragAmount * degreesPerPx).coerceIn(-45f, 45f)
                            val snapped = if (abs(nextAngle) < 0.35f) 0f else nextAngle
                            latestOnAngleChange(snapped)
                        }
                    }
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val pxPerDegree = size.width / 90f // -45° to +45° visible span

                for (deg in -45..45) {
                    val offsetFromCurrent = deg - currentAngle
                    val x = centerX + offsetFromCurrent * pxPerDegree
                    if (x < 0f || x > size.width) continue

                    val isZero = deg == 0
                    val isMajor = deg % 15 == 0
                    val isMedium = deg % 5 == 0

                    if (!isMajor && !isMedium) continue

                    val tickHalfH = when {
                        isZero -> size.height * 0.45f
                        isMajor -> size.height * 0.35f
                        else -> size.height * 0.22f
                    }
                    val strokeW = when {
                        isZero -> 2.5.dp.toPx()
                        isMajor -> 1.8.dp.toPx()
                        else -> 1.2.dp.toPx()
                    }
                    val color = when {
                        isZero -> primaryColor
                        isMajor -> tickColor
                        else -> subTickColor
                    }

                    drawLine(
                        color = color,
                        start = Offset(x, centerY - tickHalfH),
                        end = Offset(x, centerY + tickHalfH),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }

                // Fixed center indicator needle
                drawLine(
                    color = primaryColor,
                    start = Offset(centerX, 2.dp.toPx()),
                    end = Offset(centerX, size.height - 2.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // +90° Step Button
            FilledTonalIconButton(
                onClick = { onRotateStep(90f) },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.RotateRight,
                    contentDescription = "Rotar +90°",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun calculateAspectCropRect(currentCropRect: Rect, photoBounds: Rect, targetRatio: Float): Rect {
    if (photoBounds == Rect.Zero || photoBounds.width <= 0f || photoBounds.height <= 0f) return currentCropRect

    val maxW = photoBounds.width
    val maxH = photoBounds.height
    val photoRatio = maxW / maxH

    val newW: Float
    val newH: Float
    if (targetRatio > photoRatio) {
        newW = maxW
        newH = maxW / targetRatio
    } else {
        newH = maxH
        newW = maxH * targetRatio
    }

    val desiredCenter = if (currentCropRect != Rect.Zero && currentCropRect != photoBounds) {
        currentCropRect.center
    } else {
        photoBounds.center
    }

    val left = (desiredCenter.x - newW / 2f).coerceIn(photoBounds.left, photoBounds.right - newW)
    val top = (desiredCenter.y - newH / 2f).coerceIn(photoBounds.top, photoBounds.bottom - newH)
    return Rect(left, top, left + newW, top + newH)
}
