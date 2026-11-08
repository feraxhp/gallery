package com.feraxhp.gallery.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetadataItem(
    icon: ImageVector,
    label: String,
    value: String?,
    errorText: String = "Desconocido",
    onIconClick: (() -> Unit)? = null,
    onValueClick: (() -> Unit)? = null,
    onValueLongClick: (() -> Unit)? = null,
    customValueContent: (@Composable () -> Unit)? = null
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
                .weight(1f)
                .padding(start = 16.dp)
                .then(
                    if (onValueClick != null || onValueLongClick != null) {
                        Modifier.combinedClickable(
                            onClick = { onValueClick?.invoke() },
                            onLongClick = { onValueLongClick?.invoke() }
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (customValueContent != null) {
                customValueContent()
            } else if (value != null) {
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
