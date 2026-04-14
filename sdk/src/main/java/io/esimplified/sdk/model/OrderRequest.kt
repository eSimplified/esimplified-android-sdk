package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderRequest(
    @SerialName("order_uuid") val orderId: String,
    @SerialName("include_base64_qr_code") val includeQrCode: Boolean,
)
