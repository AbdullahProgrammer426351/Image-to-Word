package com.image.word.converter.convert.docx.ui.navigation

import com.image.word.converter.convert.docx.data.local.db.ConvertedItem
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination {
    @Serializable
    data object Splash : Destination

    @Serializable
    data object Dashboard : Destination

    @Serializable
    data object Camera : Destination

    @Serializable
    data class BatchPreview(val imageUris: List<String>) : Destination

    @Serializable
    data object UrlImport : Destination

    @Serializable
    data class Processing(val imageUris: List<String>) : Destination

    @Serializable
    data class Result(val items: List<ConvertedItem>) : Destination

    @Serializable
    data object Premium : Destination
}

@Serializable
enum class Tab(val title: String) {
    Home("Home"),
    Saved("Saved"),
    Settings("Settings")
}
