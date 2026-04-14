package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Country(
    @SerialName("country_name")
    val name: String,
    @SerialName("country_code")
    val code: String,
    @SerialName("country_flag")
    val flag: String,
    @SerialName("country_flag_css")
    val flagCss: String,
    @SerialName("country_name_slug")
    val slug: String,
    @SerialName("supported_countries")
    val destinations: List<SupportedCountry> = listOf(),
    @SerialName("is_region")
    val isRegion: Boolean = false,
    @SerialName("from_price")
    val fromPrice: Double? = null,
    @SerialName("currency")
    val currency: String? = null,
    @SerialName("currency_obj")
    val currencyObject: CurrencyObject? = null,
) {

    val isGlobal: Boolean = code == GLOBAL_CODE

    companion object {
        private const val GLOBAL_CODE = "2A"
    }
}
