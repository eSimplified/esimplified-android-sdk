package io.esimplified.sdk.repository

import io.esimplified.sdk.model.Promo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface MarketingRepository {

    // region Reads
    suspend fun fetchPromos(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = MARKETING_TTL,
    ): List<Promo> = fetchPromosResult(language, forceRefresh, cacheTTL).value
    // endregion

    // region Result reads
    suspend fun fetchPromosResult(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = MARKETING_TTL,
    ): RepositoryResult<List<Promo>>
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val MARKETING_TTL: Duration = 3600.seconds
    }
}
