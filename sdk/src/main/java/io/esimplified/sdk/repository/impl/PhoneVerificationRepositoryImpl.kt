package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.PhoneOtpChannel
import io.esimplified.sdk.model.PhoneOtpSendRequest
import io.esimplified.sdk.model.PhoneOtpSendResponse
import io.esimplified.sdk.model.PhoneOtpVerifyRequest
import io.esimplified.sdk.model.PhoneOtpVerifyResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.PhoneVerificationRepository
import io.esimplified.sdk.repository.apiRead

internal class PhoneVerificationRepositoryImpl(
    private val apiService: ApiService,
) : PhoneVerificationRepository {

    // region Phone Verification
    override suspend fun sendCode(phoneNumber: String, channel: PhoneOtpChannel): PhoneOtpSendResponse =
        apiRead { apiService.sendPhoneOtp(PhoneOtpSendRequest(phoneNumber = phoneNumber, channel = channel)) }

    override suspend fun verifyCode(code: String): PhoneOtpVerifyResponse =
        apiRead { apiService.verifyPhoneOtp(PhoneOtpVerifyRequest(code = code)) }
    // endregion
}
