package io.esimplified.sdk.repository

import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.KredsQuoteResponse
import io.esimplified.sdk.model.MokafaaOtpInitiateRequest
import io.esimplified.sdk.model.MokafaaOtpInitiateResponse
import io.esimplified.sdk.model.MokafaaOtpValidateResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface LoyaltyRepository {

    // region Reads
    suspend fun getLoyaltyBalance(
        forceRefresh: Boolean = true,
        cacheTTL: Duration = KREDS_BALANCE_TTL,
    ): KredsLoyaltyBalanceResponse
    // endregion

    // region Result reads
    suspend fun getLoyaltyBalanceResult(
        forceRefresh: Boolean = true,
        cacheTTL: Duration = KREDS_BALANCE_TTL,
    ): RepositoryResult<KredsLoyaltyBalanceResponse?> =
        RepositoryResult(getLoyaltyBalance(forceRefresh, cacheTTL))
    // endregion

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
        packageTypeId: Int? = null,
    ): MokafaaOtpValidateResponse

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val KREDS_BALANCE_TTL: Duration = 3600.seconds
    }
}
