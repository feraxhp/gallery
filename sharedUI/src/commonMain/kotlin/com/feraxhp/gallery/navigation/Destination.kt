package com.feraxhp.gallery.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination {
    @Serializable
    data object Permissions : Destination
    @Serializable
    data object Gallery : Destination
    @Serializable
    data object Albums : Destination
}
