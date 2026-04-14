package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KredsQuoteRequest(
    @SerialName("package_type_id") val packageTypeId: Int,
    @SerialName("loyalty_points_amount") val loyaltyPointsAmount: Double,
)
