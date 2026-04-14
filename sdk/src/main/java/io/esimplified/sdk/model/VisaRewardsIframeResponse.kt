package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisaRewardsIframeResponse(
    @SerialName("created")
    val created: Boolean = false,
    @SerialName("token")
    val token: String? = null,
    @SerialName("alias_id")
    val aliasId: String? = null,
    @SerialName("iframe_url")
    val iframeUrl: String? = null,
    @SerialName("correlation_id")
    val correlationId: String? = null,
    @SerialName("status")
    val status: Int? = null,
    @SerialName("eligible")
    val eligible: Boolean = false,
    @SerialName("redeemed")
    val redeemed: Boolean = false,
)
