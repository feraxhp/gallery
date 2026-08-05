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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isGranted)
                MaterialTheme.colorScheme.surfaceContainerLow
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icono del permiso
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (isGranted)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Título del permiso
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Descripción debajo
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                contentAlignment = Alignment.Center
            ) {
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Otorgado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Button(
                        onClick = onClick,
                        shape = MaterialTheme.shapes.large,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Configurar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
        }

}

@Preview
@Composable
fun PreviewPermisionCard() {
    Column {
        PermissionCard(
            title = "Acceso a Fotos y Videos",
            description = "Permite leer y mostrar las imágenes y videos almacenados en tu dispositivo.",
            icon = Icons.Default.PhotoLibrary,
            isGranted = true,
            onClick = {},
        )
        Spacer(modifier = Modifier.height(12.dp))
        PermissionCard(
            title = "Acceso a Fotos y Videos",
            description = "Permite leer y mostrar las imágenes y videos almacenados en tu dispositivo.",
            icon = Icons.Default.PhotoLibrary,
            isGranted = false,
            onClick = {},
        )
    }
}