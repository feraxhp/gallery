package com.feraxhp.gallery.viewmodel

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import com.feraxhp.gallery.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.abs
import kotlin.math.roundToInt

// UI States
sealed interface EditState {
    data object IDLE : EditState
    data class EDITING(val activeCategory: Category, val activeTool: Tool? = null) : EditState
}

data class AccumulatedEditState(
    val totalRotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val straightenAngle: Float = 0f,
    val cropRect: Rect? = null,
    val ratio: Float? = null
)

data class EditUiState(
    val state: EditState = EditState.IDLE,
    val undoStack: List<EditOperation> = emptyList(),
    val redoStack: List<EditOperation> = emptyList(),
    val activePreviewOperation: EditOperation? = null,
    val gridMode: GridMode = GridMode.NONE,
    val boundsMode: BoundsMode = BoundsMode.FIT,
    val showDiscardDialog: Boolean = false
)

fun Rect.toNormalized(photoBounds: Rect): Rect {
    if (photoBounds.width <= 0f || photoBounds.height <= 0f) return Rect(0f, 0f, 1f, 1f)
    val l = ((left - photoBounds.left) / photoBounds.width).coerceIn(0f, 1f)
    val t = ((top - photoBounds.top) / photoBounds.height).coerceIn(0f, 1f)
    val r = ((right - photoBounds.left) / photoBounds.width).coerceIn(l, 1f)
    val b = ((bottom - photoBounds.top) / photoBounds.height).coerceIn(t, 1f)
    return Rect(l, t, r, b)
}

fun Rect.fromNormalized(photoBounds: Rect): Rect {
    if (photoBounds.width <= 0f || photoBounds.height <= 0f) return photoBounds
    return Rect(
        left = photoBounds.left + left * photoBounds.width,
        top = photoBounds.top + top * photoBounds.height,
        right = photoBounds.left + right * photoBounds.width,
        bottom = photoBounds.top + bottom * photoBounds.height
    )
}

fun EditUiState.hasChanges(): Boolean {
    if (undoStack.isEmpty() && activePreviewOperation == null) return false
    val acc = getAccumulatedState()
    val hasRotation = (abs(acc.totalRotation.roundToInt()) % 360) != 0
    val hasFlip = acc.flipH || acc.flipV
    val hasStraighten = abs(acc.straightenAngle) > 0.05f
    val hasCrop = acc.cropRect != null && (
        acc.cropRect.left > 0.002f ||
        acc.cropRect.top > 0.002f ||
        acc.cropRect.right < 0.998f ||
        acc.cropRect.bottom < 0.998f
    )
    return hasRotation || hasFlip || hasStraighten || hasCrop
}

fun EditUiState.getAccumulatedState(): AccumulatedEditState {
    var totalRotation = 0f
    var flipH = false
    var flipV = false
    var straightenAngle = 0f
    var cropRect: Rect? = null
    var ratio: Float? = null

    val ops = undoStack + listOfNotNull(activePreviewOperation)
    for (op in ops) {
        when (op) {
            is EditOperation.CropOp -> {
                if (op.bounds != Rect.Zero) {
                    cropRect = op.bounds
                }
                ratio = op.ratio
            }
            is EditOperation.RotateStepOp -> {
                val steps = (((op.degrees / 90f).toInt()) % 4 + 4) % 4
                if (steps % 2 != 0) {
                    val tmp = flipH
                    flipH = flipV
                    flipV = tmp
                }
                totalRotation = (totalRotation + op.degrees).let { (it % 360f + 360f) % 360f }

                cropRect = cropRect?.let { rect ->
                    var r = rect
                    repeat(steps) {
                        r = Rect(
                            left = (1f - r.bottom).coerceIn(0f, 1f),
                            top = r.left.coerceIn(0f, 1f),
                            right = (1f - r.top).coerceIn(0f, 1f),
                            bottom = r.right.coerceIn(0f, 1f)
                        )
                    }
                    r
                }
                if (steps % 2 != 0 && ratio != null && ratio > 0f) {
                    ratio = 1f / ratio
                }
            }
            is EditOperation.FlipOp -> {
                if (op.flipH) {
                    flipH = !flipH
                    cropRect = cropRect?.let { rect ->
                        Rect(
                            left = (1f - rect.right).coerceIn(0f, 1f),
                            top = rect.top,
                            right = (1f - rect.left).coerceIn(0f, 1f),
                            bottom = rect.bottom
                        )
                    }
                }
                if (op.flipV) {
                    flipV = !flipV
                    cropRect = cropRect?.let { rect ->
                        Rect(
                            left = rect.left,
                            top = (1f - rect.bottom).coerceIn(0f, 1f),
                            right = rect.right,
                            bottom = (1f - rect.top).coerceIn(0f, 1f)
                        )
                    }
                }
            }
            is EditOperation.StraightenOp -> {
                straightenAngle = op.angle
            }
        }
    }

    return AccumulatedEditState(
        totalRotation = totalRotation,
        flipH = flipH,
        flipV = flipV,
        straightenAngle = straightenAngle,
        cropRect = cropRect,
        ratio = ratio
    )
}

enum class GridMode { NONE, RULE_OF_THIRDS, GOLDEN_RATIO, FOURTHS }
enum class BoundsMode { FIT, CROP }

class EditViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    fun setCategory(category: Category?) {
        _uiState.update { currentState ->
            if (category == null) {
                val state = commitPreviewOperation(currentState)
                state.copy(state = EditState.IDLE)
            } else {
                currentState.copy(state = EditState.EDITING(activeCategory = category))
            }
        }
    }

    fun setTool(tool: Tool?) {
        _uiState.update { currentState ->
            val editState = currentState.state
            if (editState is EditState.EDITING) {
                val stateWithCommit = commitPreviewOperation(currentState)
                stateWithCommit.copy(state = editState.copy(activeTool = tool))
            } else {
                currentState
            }
        }
    }

    fun updatePreviewOperation(operation: EditOperation?) {
        _uiState.update { currentState ->
            when (operation) {
                null -> currentState.copy(activePreviewOperation = null)
                is EditOperation.StraightenOp -> currentState.copy(activePreviewOperation = operation)
                else -> {
                    val committed = commitPreviewOperation(currentState)
                    committed.copy(
                        undoStack = committed.undoStack + operation,
                        redoStack = emptyList(),
                        activePreviewOperation = null
                    )
                }
            }
        }
    }

    fun commitPreview() {
        _uiState.update { currentState ->
            commitPreviewOperation(currentState)
        }
    }

    private fun commitPreviewOperation(currentState: EditUiState): EditUiState {
        val previewOp = currentState.activePreviewOperation
        return if (previewOp != null) {
            val newUndo = currentState.undoStack + previewOp
            currentState.copy(
                undoStack = newUndo,
                redoStack = emptyList(),
                activePreviewOperation = null
            )
        } else {
            currentState
        }
    }

    fun undo() {
        _uiState.update { currentState ->
            val committedState = commitPreviewOperation(currentState)
            if (committedState.undoStack.isNotEmpty()) {
                val lastOp = committedState.undoStack.last()
                val newUndo = committedState.undoStack.dropLast(1)
                val newRedo = committedState.redoStack + lastOp
                committedState.copy(undoStack = newUndo, redoStack = newRedo)
            } else {
                committedState
            }
        }
    }

    fun redo() {
        _uiState.update { currentState ->
            val committedState = commitPreviewOperation(currentState)
            if (committedState.redoStack.isNotEmpty()) {
                val lastOp = committedState.redoStack.last()
                val newRedo = committedState.redoStack.dropLast(1)
                val newUndo = committedState.undoStack + lastOp
                committedState.copy(undoStack = newUndo, redoStack = newRedo)
            } else {
                committedState
            }
        }
    }

    fun setGridMode(mode: GridMode) {
        _uiState.update { it.copy(gridMode = mode) }
    }

    fun setBoundsMode(mode: BoundsMode) {
        _uiState.update { it.copy(boundsMode = mode) }
    }

    fun showDiscardDialog(show: Boolean) {
        _uiState.update { it.copy(showDiscardDialog = show) }
    }

    fun discardChanges() {
        _uiState.value = EditUiState()
    }

    // Returns true if activity/screen should be closed
    fun onBack(): Boolean {
        val current = _uiState.value
        return when (current.state) {
            is EditState.EDITING -> {
                setCategory(null)
                false
            }
            EditState.IDLE -> {
                if (current.hasChanges()) {
                    showDiscardDialog(true)
                    false
                } else {
                    discardChanges()
                    true
                }
            }
        }
    }
}
