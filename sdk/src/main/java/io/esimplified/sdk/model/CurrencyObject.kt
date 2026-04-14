package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CurrencyObject(
    @SerialName("symbol")
    val symbol: String,
    @SerialName("iso")
    val isoCode: String
)
