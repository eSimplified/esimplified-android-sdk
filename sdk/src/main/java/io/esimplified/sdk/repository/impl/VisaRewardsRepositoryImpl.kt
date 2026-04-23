package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.VisaRewardsRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.VisaRewardsIframeResponse
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class VisaRewardsRepositoryImpl(
    private val apiService: ApiService
) : VisaRewardsRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Visa Rewards
    override suspend fun getIframe(isEU: Boolean): VisaRewardsIframeResponse {
        try {
            return apiService.getPromotionIframe()
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun verify(token: String): VisaRewardsResponse {
        try {
            return apiService.validatePromotion(token = token)
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun activate(token: String, rewardCode: String): VisaRewardsResponse {
        try {
            return apiService.activatePromotion(
                token = token,
                rewardCode = rewardCode
            )
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                errorResponse.detail ?: errorResponse.message ?: errorResponse.error
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
