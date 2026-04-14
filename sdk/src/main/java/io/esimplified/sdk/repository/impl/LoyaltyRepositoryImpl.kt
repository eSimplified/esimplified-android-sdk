package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.LoyaltyRepository

import io.esimplified.sdk.model.KredsQuoteRequest
import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.KredsQuoteResponse
import io.esimplified.sdk.network.ApiService

internal class LoyaltyRepositoryImpl(
    private val apiService: ApiService
) : LoyaltyRepository {

    // region Loyalty
    override suspend fun getLoyaltyBalance(): KredsLoyaltyBalanceResponse {
        return apiService.getLoyaltyPoints()
    }

    override suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse {
        return apiService.sendKredsQuote(KredsQuoteRequest(packageTypeId, loyaltyPointsAmount))
    }
    // endregion
}
