package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.LoyaltyRepository

import io.esimplified.sdk.model.KredsQuoteRequest
import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.KredsQuoteResponse
import io.esimplified.sdk.model.LoyaltyProvider
import io.esimplified.sdk.model.MokafaaOtpInitiateRequest
import io.esimplified.sdk.model.MokafaaOtpInitiateResponse
import io.esimplified.sdk.model.MokafaaOtpValidateRequest
import io.esimplified.sdk.model.MokafaaOtpValidateResponse
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.LoyaltyApiException
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import retrofit2.HttpException

internal class LoyaltyRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : LoyaltyRepository {

    // region Loyalty
    override suspend fun getLoyaltyBalance(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): KredsLoyaltyBalanceResponse = getLoyaltyBalanceResult(forceRefresh, cacheTTL).valueOrThrow()

    override suspend fun getLoyaltyBalanceResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<KredsLoyaltyBalanceResponse?> =
        cache.cachedResult<KredsLoyaltyBalanceResponse>(KREDS_BALANCE_KEY, forceRefresh, cacheTTL) {
            try {
                apiService.getLoyaltyPoints()
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }

    override suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse {
        try {
            return apiService.sendKredsQuote(KredsQuoteRequest(packageTypeId, loyaltyPointsAmount))
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }
    // endregion

    override suspend fun getMokafaaQuote(packageTypeId: Int, loyaltyPointsToUse: Int): KredsQuoteResponse {
        try {
            return apiService.sendKredsQuote(
                KredsQuoteRequest(
                    packageTypeId = packageTypeId,
                    loyaltyProvider = LoyaltyProvider.MOKAFAA,
                    loyaltyPointsToUse = loyaltyPointsToUse,
                )
            )
        } catch (e: HttpException) {
            throw LoyaltyApiException(e.code(), ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }

    override suspend fun initiateMokafaaOtp(purpose: String, platform: String): MokafaaOtpInitiateResponse {
        try {
            return apiService.initiateMokafaaOtp(MokafaaOtpInitiateRequest(purpose, platform))
        } catch (e: HttpException) {
            throw LoyaltyApiException(e.code(), ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }

    override suspend fun validateMokafaaOtp(
        sessionId: String,
        otp: String,
        points: Int?,
        packageTypeId: Int?,
    ): MokafaaOtpValidateResponse {
        try {
            return apiService.validateMokafaaOtp(
                MokafaaOtpValidateRequest(sessionId, otp, points, packageTypeId)
            )
        } catch (e: HttpException) {
            throw LoyaltyApiException(e.code(), ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }

    // region Cache
    override suspend fun invalidateCache() {
        cache.remove(KREDS_BALANCE_KEY)
    }
    // endregion

    private companion object {
        const val KREDS_BALANCE_KEY = "kreds_balance"
    }
}
