package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SavedPaymentMethod(
    @SerialName("payment_method_id") val paymentMethodId: String = "",
    @SerialName("card_brand") val cardBrand: String = "",
    @SerialName("card_last4") val cardLast4: String = "",
    @SerialName("card_exp_month") val cardExpMonth: Int? = null,
    @SerialName("card_exp_year") val cardExpYear: Int? = null,
)
