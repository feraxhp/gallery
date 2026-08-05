package com.feraxhp.gallery.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun NavigationItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit = {},
) {
    val color = animateColorAsState(
        if (selected) { MaterialTheme.colorScheme.onSurfaceVariant }
        else { MaterialTheme.colorScheme.onSurface }
    )

    val containerColor = animateColorAsState(
        if (selected) { MaterialTheme.colorScheme.surfaceVariant }
        else { Color.Transparent }
    )

    val with = animateDpAsState(
        if (selected) { 78.dp }
//        else { 68.dp }
        else { 78.dp }
    )

    TooltipBox(
        positionProvider = TooltipDefaults
            .rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                modifier =
                    Modifier.semantics {
                        // TODO(b/496338253): Remove this modifier once bug
                        //  where tooltip text is not announced by a11y screen
                        //  readers is resolved.
                        liveRegion = LiveRegionMode.Assertive
                        paneTitle = label
                    },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) { Text(label) }
        },
        state = rememberTooltipState(),
        hasAction = true
    ) {
        IconButton(
            modifier = Modifier
                .width(with.value)
            ,
            colors = IconButtonDefaults.iconButtonColors().copy(
                containerColor = containerColor.value
            ),
            onClick = onClick,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier//.padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color.value,
                )
                Text(
                    text = label,
                    color = color.value,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

    }
}

@Preview
@Composable
fun NavigationItemPreview() {
    Row {
        NavigationItem(true, "fotos", Icons.Default.PhotoLibrary)
        Spacer(Modifier.height(8.dp))
        NavigationItem(false, "gallery", Icons.Default.Photo)
    }
}