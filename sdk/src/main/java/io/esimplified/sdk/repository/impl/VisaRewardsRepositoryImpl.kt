package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.VisaRewardsRepository

import io.esimplified.sdk.model.VisaRewardsIframeResponse
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import retrofit2.HttpException

internal class VisaRewardsRepositoryImpl(
    private val apiService: ApiService
) : VisaRewardsRepository {

    // region Visa Rewards
    override suspend fun getIframe(isEU: Boolean): VisaRewardsIframeResponse {
        try {
            return apiService.getPromotionIframe()
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }

    override suspend fun verify(token: String): VisaRewardsResponse {
        try {
            return apiService.validatePromotion(token = token)
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }

    override suspend fun activate(token: String, rewardCode: String): VisaRewardsResponse {
        try {
            return apiService.activatePromotion(
                token = token,
                rewardCode = rewardCode
            )
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }
    // endregion

}
