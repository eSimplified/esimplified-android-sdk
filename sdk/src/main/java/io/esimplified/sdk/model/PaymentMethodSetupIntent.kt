package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentMethodSetupIntent(
    @SerialName("client_secret") val clientSecret: String = "",
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("publishable_key") val publishableKey: String? = null,
)
