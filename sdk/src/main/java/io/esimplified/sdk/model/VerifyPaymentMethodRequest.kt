package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyPaymentMethodRequest(
    @SerialName("payment_method_id") val paymentMethodId: String,
    @SerialName("discount_code") val discountCode: String,
)
