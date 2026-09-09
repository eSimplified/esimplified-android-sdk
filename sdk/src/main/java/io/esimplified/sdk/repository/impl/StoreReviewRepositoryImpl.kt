package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.RatingApiResponse
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.StoreReviewRepository
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import retrofit2.HttpException

internal class StoreReviewRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : StoreReviewRepository {

    // region Store review
    override suspend fun fetchStoreReview(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RatingApiResponse = fetchStoreReviewResult(forceRefresh, cacheTTL).valueOrThrow()

    override suspend fun fetchStoreReviewResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<RatingApiResponse?> =
        cache.cachedResult<RatingApiResponse>(STORE_REVIEW_KEY, forceRefresh, cacheTTL) {
            try {
                apiService.getStoreReview()
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.remove(STORE_REVIEW_KEY)
    }
    // endregion

    private companion object {
        const val STORE_REVIEW_KEY = "store_review"
    }
}
