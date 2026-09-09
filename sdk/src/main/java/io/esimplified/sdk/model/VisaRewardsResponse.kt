package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisaRewardsResponse(
    @SerialName("eligible") val eligible: Boolean = false,
    @SerialName("status") val status: Int? = null,
    @SerialName("detail") val detail: String? = null,
    @SerialName("details") val details: String? = null,
    @SerialName("used_count") val used: Int? = null,
    @SerialName("reward_type") val reward: String? = null,
    @SerialName("allowed_count") val allowed: Int? = null,
    @SerialName("remaining_count") val remaining: Int? = null,
    @SerialName("redeemed") val redeemed: Boolean = false,
    @SerialName("redirect_url") val redirectURl: String? = null,
    @SerialName("validity_days") val validityDays: Int? = null,
    @SerialName("data_GB") val dataGB: Double? = null,
) {
    val remainingOrAllowed: Int?
        get() = remaining ?: allowed
}
