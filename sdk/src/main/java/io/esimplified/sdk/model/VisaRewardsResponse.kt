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

    val orderUuid: String?
        get() {
            val url = redirectURl?.takeIf { it.isNotEmpty() } ?: return null
            val query = url.substringAfter('?', missingDelimiterValue = "")
            val id = query.split('&')
                .map { it.split('=', limit = 2) }
                .firstOrNull { it.size == 2 && it[0] == ORDER_ID_QUERY_ITEM }
                ?.get(1)
                ?.takeIf { it.isNotEmpty() }
            if (id != null) return id
            if (!url.contains('=')) return null
            return url.substringAfterLast('=').takeIf { it.isNotEmpty() }
        }
}

private const val ORDER_ID_QUERY_ITEM = "id"
