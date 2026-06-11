package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object LoyaltyProvider {
    const val KREDS = "kreds"
    const val MOKAFAA = "mokafaa"
}

@Serializable
data class MokafaaOtpInitiateRequest(
    @SerialName("purpose") val purpose: String,
    @SerialName("platform") val platform: String,
) {

    object Purpose {
        const val ENROLLMENT = "enrollment"
        const val CHECKOUT = "checkout"
    }

    object Platform {
        const val ANDROID = "android"
    }
}

@Serializable
data class MokafaaOtpInitiateResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("expires_at") val expiresAt: String,
)

@Serializable
data class MokafaaOtpValidateRequest(
    @SerialName("session_id") val sessionId: String,
    @SerialName("otp") val otp: String,
    @SerialName("points") val points: Int? = null,
)

@Serializable
data class MokafaaOtpValidateResponse(
    @SerialName("status") val status: String,
    @SerialName("points_redeemed") val pointsRedeemed: Int? = null,
) {

    object Status {
        const val CONFIRMED = "confirmed"
        const val REVERSED = "reversed"
    }
}
