// Copyright (C) 2026 feraxhp
// 
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
package com.feraxhp.gallery

import androidx.compose.runtime.*
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.feraxhp.gallery.navigation.Destination
import com.feraxhp.gallery.repository.ImageRepository
import com.feraxhp.gallery.screens.GalleryScreen
import com.feraxhp.gallery.screens.PermissionsScreen
import com.feraxhp.ktheme.DynamicTheme

@Composable
fun App(
    repository: ImageRepository,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    var backStack by remember {
        mutableStateOf(
            listOf<Destination>(
                if (hasPermission) Destination.Gallery else Destination.Permissions
            )
        )
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission && backStack.lastOrNull() == Destination.Permissions) {
            backStack = listOf(Destination.Gallery)
        }
    }

    DynamicTheme {
        NavDisplay(
            backStack = backStack,
            onBack = {
                if (backStack.size > 1) {
                    backStack = backStack.dropLast(1)
                }
            },
            entryProvider = { key: Destination ->
                when (key) {
                    Destination.Permissions -> NavEntry(key) {
                        PermissionsScreen(onRequestPermission = onRequestPermission)
                    }
                    Destination.Gallery -> NavEntry(key) {
                        GalleryScreen(repository = repository)
                    }
                }
            }
        )
    }
}
