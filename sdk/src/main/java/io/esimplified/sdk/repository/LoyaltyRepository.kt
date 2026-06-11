package io.esimplified.sdk.repository

import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.KredsQuoteResponse
import io.esimplified.sdk.model.MokafaaOtpInitiateRequest
import io.esimplified.sdk.model.MokafaaOtpInitiateResponse
import io.esimplified.sdk.model.MokafaaOtpValidateResponse

interface LoyaltyRepository {
    suspend fun getLoyaltyBalance(): KredsLoyaltyBalanceResponse
    suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse
    suspend fun getMokafaaQuote(packageTypeId: Int, loyaltyPointsToUse: Int): KredsQuoteResponse
    suspend fun initiateMokafaaOtp(
        purpose: String,
        platform: String = MokafaaOtpInitiateRequest.Platform.ANDROID,
    ): MokafaaOtpInitiateResponse

    suspend fun validateMokafaaOtp(
        sessionId: String,
        otp: String,
        points: Int? = null,
    ): MokafaaOtpValidateResponse
}
