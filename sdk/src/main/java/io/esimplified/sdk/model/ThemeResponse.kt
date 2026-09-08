package io.esimplified.sdk.model

import kotlinx.serialization.Serializable

// region Theme response
@Serializable
internal data class ThemeResponse(
    val version: String? = null,
    val cdnBase: String? = null,
    val pages: Map<String, ThemePage> = emptyMap(),
    val destinations: Map<String, ThemeDestination> = emptyMap(),
)
// endregion

// region Theme page
@Serializable
data class ThemePage(
    val urlPath: String? = null,
    val featuredImage: ThemeImage? = null,
    val color: String? = null,
)
// endregion

// region Theme image
@Serializable
data class ThemeImage(
    val url: String,
    val accent: String? = null,
)
// endregion

// region Theme destination
@Serializable
data class ThemeDestination(
    val image: ThemeImage? = null,
    val gallery: List<String>? = null,
    val countryCode: String? = null,
)
// endregion
