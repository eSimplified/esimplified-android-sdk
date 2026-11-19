package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Localization
@Serializable
data class Localization(
    @SerialName("default_currency") val defaultCurrency: String? = null,
    val currencies: List<SupportedCurrency> = emptyList(),
    @SerialName("default_language") val defaultLanguage: String? = null,
    val languages: List<SupportedLanguage> = emptyList(),
)
// endregion

// region Localization Include
enum class LocalizationInclude(val value: String) {
    CURRENCIES("currencies"),
    LANGUAGES("languages"),
}
// endregion

// region Supported Currency
@Serializable
data class SupportedCurrency(
    val code: String = "",
    val name: String? = null,
    val symbol: String? = null,
)
// endregion

// region Supported Language
@Serializable
data class SupportedLanguage(
    val code: String = "",
    val name: String? = null,
)
// endregion
