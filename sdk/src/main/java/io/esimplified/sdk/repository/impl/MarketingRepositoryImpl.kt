package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.Promo
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.MarketingRepository
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import kotlin.time.Duration
import retrofit2.HttpException

internal class MarketingRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : MarketingRepository {

    // region Promos
    override suspend fun fetchPromosResult(
        language: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<Promo>> =
        cache.cachedListResult("$MARKETING_KEY_PREFIX$language", forceRefresh, cacheTTL) {
            try {
                apiService.getMarketingPromos().promos
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(MARKETING_KEY_PREFIX)
    }
    // endregion

    private companion object {
        const val MARKETING_KEY_PREFIX = "marketing_"
    }
}
