package com.feraxhp.gallery.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    onRequestReadPermission: () -> Unit,
    onRequestWritePermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Para que la aplicación funcione correctamente, necesitamos los siguientes permisos:",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        PermissionItem(
            label = "Acceso a Fotos y Videos (Lectura)",
            isGranted = hasReadPermission,
            onClick = onRequestReadPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            label = "Gestión de Archivos (Mover y Borrar)",
            isGranted = hasWritePermission,
            onClick = onRequestWritePermission
        )
    }
}

@Composable
private fun PermissionItem(
    label: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Otorgado",
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Button(onClick = onClick) {
                Text("Otorgar")
            }
        }
    }
}
