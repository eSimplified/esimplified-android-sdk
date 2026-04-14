package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.VisaRewardsRepository

import io.esimplified.sdk.model.VisaRewardsIframeResponse
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.network.ApiService

internal class VisaRewardsRepositoryImpl(
    private val apiService: ApiService
) : VisaRewardsRepository {

    // region Visa Rewards
    override suspend fun getIframe(isEU: Boolean): VisaRewardsIframeResponse {
        return apiService.getPromotionIframe()
    }

    override suspend fun verify(token: String): VisaRewardsResponse {
        return apiService.validatePromotion(token = token)
    }

    override suspend fun activate(token: String, rewardCode: String): VisaRewardsResponse {
        return apiService.activatePromotion(
            token = token,
            rewardCode = rewardCode
        )
    }
    // endregion
}
