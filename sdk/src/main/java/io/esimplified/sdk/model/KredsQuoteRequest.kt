package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KredsQuoteRequest(
    @SerialName("package_type_id") val packageTypeId: Int,
    @SerialName("loyalty_points_amount") val loyaltyPointsAmount: Double? = null,
    @SerialName("loyalty_provider") val loyaltyProvider: String? = null,
    @SerialName("loyalty_points_to_use") val loyaltyPointsToUse: Int? = null,
)
