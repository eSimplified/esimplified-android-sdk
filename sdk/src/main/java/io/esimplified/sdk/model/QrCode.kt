package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QrCode(
    @SerialName("image_base64")
    val imageBase64: String,
    @SerialName("image_url")
    val imageUrl: String
)
