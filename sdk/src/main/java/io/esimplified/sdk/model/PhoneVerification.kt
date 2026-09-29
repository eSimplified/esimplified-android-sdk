package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PhoneOtpChannel {
    @SerialName("sms") SMS,
    @SerialName("whatsapp") WHATSAPP,
}

@Serializable
data class PhoneOtpSendRequest(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("channel") val channel: PhoneOtpChannel,
)

@Serializable
data class PhoneOtpSendResponse(
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("channel") val channel: PhoneOtpChannel? = null,
)

@Serializable
data class PhoneOtpVerifyRequest(
    @SerialName("code") val code: String,
)

@Serializable
data class PhoneOtpVerifyResponse(
    @SerialName("phone_number") val phoneNumber: String = "",
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
)
