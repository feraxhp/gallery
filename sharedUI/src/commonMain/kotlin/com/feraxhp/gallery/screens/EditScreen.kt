package com.feraxhp.gallery.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feraxhp.gallery.components.editor.*
import com.feraxhp.gallery.model.*
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.util.BackHandler
import com.feraxhp.gallery.viewmodel.*
import kotlinx.coroutines.launch

@Composable
fun EditScreen(
    imageUri: String,
    onBack: () -> Unit,
    repository: ImageRepository? = null,
    onImageSaved: ((GalleryImage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: EditViewModel = viewModel(key = imageUri) { EditViewModel() }
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Ensure tool and edit state always starts clean when opening a new photo and resets on exit
    LaunchedEffect(imageUri) {
        viewModel.discardChanges()
    }
    DisposableEffect(imageUri) {
        onDispose {
            viewModel.discardChanges()
        }
    }

    BackHandler {
        if (viewModel.onBack()) {
            onBack()
        }
    }

    var showHelpDialog by remember { mutableStateOf<HelpDialogData?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var photoBounds by remember { mutableStateOf(Rect.Zero) }
    var imageAspectRatio by remember { mutableFloatStateOf(1f) }

    val hasChanges = remember(uiState) { uiState.hasChanges() }

    // Categories and Tools (CAT-TRANSFORM): unified "Rotar" combines 90° steps and angle dial
    val transformCategory = remember {
        Category(
            id = "cat_transform",
            name = "Transformación",
            icon = Icons.Default.Transform,
            tools = listOf(
                Tool(
                    id = "crop",
                    name = "Recortar",
                    icon = Icons.Default.Crop,
                    explanation = "Ajusta los bordes y la proporción de la imagen.",
                    onApply = { EditOperation.CropOp(Rect.Zero, null) }
                ),
                Tool(
                    id = "rotate",
                    name = "Rotar",
                    icon = Icons.AutoMirrored.Filled.RotateRight,
                    explanation = "Usa el dial para inclinar la imagen entre -45° y 45° o los botones laterales para girarla 90°.",
                    onApply = { EditOperation.StraightenOp(0f) }
                ),
                Tool(
                    id = "flip",
                    name = "Espejo",
                    icon = Icons.Default.Flip,
                    explanation = "Invierte la imagen horizontal o verticalmente.",
                    onApply = { EditOperation.FlipOp(flipH = true, flipV = false) }
                )
            )
        )
    }

    val categories = remember { listOf(transformCategory) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 3. Barra Superior [UI-TOP]
            TopNavBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                canSave = hasChanges,
                canUndo = uiState.undoStack.isNotEmpty() || uiState.activePreviewOperation != null,
                canRedo = uiState.redoStack.isNotEmpty(),
                onSave = {
                    if (isSaving) return@TopNavBar
                    viewModel.commitPreview()
                    val currentState = viewModel.uiState.value
                    if (!currentState.hasChanges()) {
                        viewModel.discardChanges()
                        onBack()
                        return@TopNavBar
                    }
                    isSaving = true
                    val accumulated = currentState.getAccumulatedState()
                    scope.launch {
                        val savedImage = repository?.saveEditedImage(
                            sourceUri = imageUri,
                            totalRotation = accumulated.totalRotation,
                            flipH = accumulated.flipH,
                            flipV = accumulated.flipV,
                            straightenAngle = accumulated.straightenAngle,
                            normalizedCropRect = accumulated.cropRect,
                            boundsMode = currentState.boundsMode
                        )
                        isSaving = false
                        viewModel.discardChanges()
                        if (savedImage != null) {
                            onImageSaved?.invoke(savedImage)
                        } else {
                            onBack()
                        }
                    }
                },
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onDiscard = {
                    if (uiState.hasChanges()) {
                        viewModel.showDiscardDialog(true)
                    } else {
                        viewModel.discardChanges()
                        onBack()
                    }
                },
                isSaving = isSaving
            )

            // 4. Contenedor Central de la Fotografía y Viewport [UI-CANVAS]
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                CanvasViewport(
                    imageUri = imageUri,
                    uiState = uiState,
                    onUpdatePreview = viewModel::updatePreviewOperation,
                    onPhotoBoundsChanged = { bounds, ratio ->
                        photoBounds = bounds
                        imageAspectRatio = ratio
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // 5. Barra Lateral Flotante de Ajustes Rápidos [UI-LEFT]
                LeftRail(
                    currentGridMode = uiState.gridMode,
                    currentBoundsMode = uiState.boundsMode,
                    onGridModeSelected = viewModel::setGridMode,
                    onBoundsModeSelected = viewModel::setBoundsMode,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            // 6. Barra Inferior y Microajustes [UI-BOTTOM]
            BottomActionMenu(
                editState = uiState.state,
                uiState = uiState,
                categories = categories,
                imageAspectRatio = imageAspectRatio,
                photoBounds = photoBounds,
                onCategorySelected = viewModel::setCategory,
                onToolSelected = viewModel::setTool,
                onUpdatePreview = viewModel::updatePreviewOperation,
                onLongPressItem = { title, desc, model ->
                    showHelpDialog = HelpDialogData(title, desc, model)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }

        // Dialogs
        showHelpDialog?.let { data ->
            ToolHelpDialog(
                title = data.title,
                description = data.description,
                exampleModel = data.exampleModel,
                onDismiss = { showHelpDialog = null }
            )
        }

        if (uiState.showDiscardDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showDiscardDialog(false) },
                title = { Text("¿Descartar todos los cambios?") },
                text = { Text("La foto original no será modificada.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.discardChanges()
                        onBack()
                    }) {
                        Text("Descartar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showDiscardDialog(false) }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

private data class HelpDialogData(val title: String, val description: String, val exampleModel: Any?)
