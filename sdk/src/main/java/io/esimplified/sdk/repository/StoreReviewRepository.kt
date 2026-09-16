package io.esimplified.sdk.repository

import io.esimplified.sdk.model.RatingApiResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface StoreReviewRepository {

    // region Reads
    suspend fun fetchStoreReview(
        forceRefresh: Boolean = false,
        cacheTTL: Duration = STORE_REVIEW_TTL,
    ): RatingApiResponse
    // endregion

    // region Result reads
    suspend fun fetchStoreReviewResult(
        forceRefresh: Boolean = false,
        cacheTTL: Duration = STORE_REVIEW_TTL,
    ): RepositoryResult<RatingApiResponse?> =
        RepositoryResult(fetchStoreReview(forceRefresh, cacheTTL))
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val STORE_REVIEW_TTL: Duration = 86400.seconds
    }
}
