package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeletePaymentMethodResponse(
    @SerialName("detail") val detail: String? = null,
)
