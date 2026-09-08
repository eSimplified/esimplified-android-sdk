package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.RatingApiResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.StoreReviewRepository
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class StoreReviewRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : StoreReviewRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

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
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.remove(STORE_REVIEW_KEY)
    }
    // endregion

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                errorResponse.detail ?: errorResponse.message ?: errorResponse.error
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val STORE_REVIEW_KEY = "store_review"
    }
}
