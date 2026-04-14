package io.esimplified.sdk.repository

import io.esimplified.sdk.model.KredsLoyaltyBalanceResponse
import io.esimplified.sdk.model.KredsQuoteResponse

interface LoyaltyRepository {
    suspend fun getLoyaltyBalance(): KredsLoyaltyBalanceResponse
    suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse
}
