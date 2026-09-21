package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.VisaRewardsRepository

import io.esimplified.sdk.model.IframeRequest
import io.esimplified.sdk.model.VisaRewardsIframeResponse
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.apiRead

internal class VisaRewardsRepositoryImpl(
    private val apiService: ApiService
) : VisaRewardsRepository {

    // region Visa Rewards
    override suspend fun getIframe(isEU: Boolean): VisaRewardsIframeResponse =
        apiRead {
            if (isEU) {
                apiService.getPromotionIframeFor(IframeRequest(vendor = EU_VENDOR))
            } else {
                apiService.getPromotionIframe()
            }
        }

    override suspend fun verify(token: String): VisaRewardsResponse =
        apiRead { apiService.validatePromotion(token = token) }

    override suspend fun activate(token: String, rewardCode: String): VisaRewardsResponse =
        apiRead {
            apiService.activatePromotion(
                token = token,
                rewardCode = rewardCode
            )
        }
    // endregion

    private companion object {
        const val EU_VENDOR = "eu"
    }
}
