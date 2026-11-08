package com.feraxhp.gallery.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.feraxhp.gallery.components.PermissionCard

@Composable
fun PermissionsScreen(
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    onRequestReadPermission: () -> Unit,
    onRequestWritePermission: () -> Unit,
    onContinue: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 0.dp
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Encabezado con icono grande
        Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Permisos requeridos",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Para que la aplicación funcione correctamente y puedas gestionar tu galería de fotos, necesitamos los siguientes permisos:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Tarjetas de permisos
        PermissionCard(
            title = "Acceso a Fotos y Videos",
            description = "Permite leer y mostrar las imágenes y videos almacenados en tu dispositivo.",
            icon = Icons.Default.PhotoLibrary,
            isGranted = hasReadPermission,
            onClick = onRequestReadPermission
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionCard(
            title = "Gestión de Archivos",
            description = "Permite organizar, mover o eliminar archivos cuando realices acciones en la galería.",
            icon = Icons.Default.Edit,
            isGranted = hasWritePermission,
            onClick = onRequestWritePermission
        )

        if (hasReadPermission && !hasWritePermission) {
            Spacer(modifier = Modifier.height(32.dp))
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continuar con acceso limitado")
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

