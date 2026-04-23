package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.LoyaltyRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.KredsQuoteRequest
import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.KredsQuoteResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class LoyaltyRepositoryImpl(
    private val apiService: ApiService
) : LoyaltyRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Loyalty
    override suspend fun getLoyaltyBalance(): KredsLoyaltyBalanceResponse {
        try {
            return apiService.getLoyaltyPoints()
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse {
        try {
            return apiService.sendKredsQuote(KredsQuoteRequest(packageTypeId, loyaltyPointsAmount))
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
