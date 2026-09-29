package io.esimplified.sdk.repository

import io.esimplified.sdk.model.PhoneOtpChannel
import io.esimplified.sdk.model.PhoneOtpSendResponse
import io.esimplified.sdk.model.PhoneOtpVerifyResponse

interface PhoneVerificationRepository {
    suspend fun sendCode(phoneNumber: String, channel: PhoneOtpChannel): PhoneOtpSendResponse
    suspend fun verifyCode(code: String): PhoneOtpVerifyResponse
}
