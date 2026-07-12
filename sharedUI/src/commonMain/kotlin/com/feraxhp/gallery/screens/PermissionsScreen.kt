package com.feraxhp.gallery.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onRequestPermission: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Permisos Requeridos") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Para mostrar tus fotos, necesitamos permiso para acceder al almacenamiento.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(onClick = onRequestPermission) {
                Text("Otorgar Permiso")
            }
        }
    }
}
