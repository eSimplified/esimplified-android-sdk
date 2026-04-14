package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VerifyEmailRequest(
    @SerialName("email") val email: String,
    @SerialName("email_verification_token") val token: String,
    @SerialName("order_uuid") val orderUUID: String?,
)

@Serializable
data class VerifyEmailResponse(
    @SerialName("email")
    val email: String? = null,
    val detail: String? = null,
    @SerialName("email_verified")
    val isVerified: Boolean = false,
)
