package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RatingApiResponse(
    @SerialName("store_name")
    val storeName: String,
    @SerialName("review_count")
    val reviewCount: Int,
    @SerialName("results_count")
    val resultsCount: Int,
    @SerialName("verdict")
    val verdict: String,
    @SerialName("average_rating")
    val rating: Double?
)
