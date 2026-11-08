package com.feraxhp.gallery.components.editor

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.GridGoldenratio
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.feraxhp.gallery.viewmodel.BoundsMode
import com.feraxhp.gallery.viewmodel.GridMode

@Composable
fun LeftRail(
    currentGridMode: GridMode,
    currentBoundsMode: BoundsMode,
    onGridModeSelected: (GridMode) -> Unit,
    onBoundsModeSelected: (BoundsMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedItem by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .padding(start = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Grid Options
        RailItem(
            icon = getGridIcon(currentGridMode),
            isExpanded = expandedItem == "grid",
            onClick = { expandedItem = if (expandedItem == "grid") null else "grid" }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GridMode.entries.forEach { mode ->
                    val isSelected = mode == currentGridMode
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape
                            )
                            .clickable {
                                onGridModeSelected(mode)
                                expandedItem = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getGridIcon(mode),
                            contentDescription = mode.name,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // Bounds Options
        RailItem(
            icon = getBoundsIcon(currentBoundsMode),
            isExpanded = expandedItem == "bounds",
            onClick = { expandedItem = if (expandedItem == "bounds") null else "bounds" }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BoundsMode.entries.forEach { mode ->
                    val isSelected = mode == currentBoundsMode
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                CircleShape
                            )
                            .clickable {
                                onBoundsModeSelected(mode)
                                expandedItem = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getBoundsIcon(mode),
                            contentDescription = mode.name,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    icon: ImageVector,
    isExpanded: Boolean,
    onClick: () -> Unit,
    expandedContent: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(44.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .height(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                expandedContent()
            }
        }
    }
}

private fun getGridIcon(mode: GridMode): ImageVector {
    return when (mode) {
        GridMode.NONE -> Icons.Default.GridOff
        GridMode.RULE_OF_THIRDS -> Icons.Default.Grid3x3
        GridMode.GOLDEN_RATIO -> Icons.Default.GridGoldenratio
        GridMode.FOURTHS -> Icons.Default.Grid4x4
    }
}

private fun getBoundsIcon(mode: BoundsMode): ImageVector {
    return when (mode) {
        BoundsMode.FIT -> Icons.Default.FitScreen
        BoundsMode.CROP -> Icons.Default.CropFree
    }
}
