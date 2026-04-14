package io.esimplified.sdk.repository

import io.esimplified.sdk.model.VisaRewardsIframeResponse
import io.esimplified.sdk.model.VisaRewardsResponse

interface VisaRewardsRepository {
    suspend fun getIframe(isEU: Boolean): VisaRewardsIframeResponse
    suspend fun verify(token: String): VisaRewardsResponse
    suspend fun activate(token: String, rewardCode: String): VisaRewardsResponse
}
