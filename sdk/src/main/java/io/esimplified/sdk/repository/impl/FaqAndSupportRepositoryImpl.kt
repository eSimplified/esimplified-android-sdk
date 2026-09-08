package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.Faq
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.FaqAndSupportRepository
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class FaqAndSupportRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : FaqAndSupportRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Destination FAQs
    override suspend fun fetchDestinationFaqs(
        countryNameSlug: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<Faq> = fetchDestinationFaqsResult(countryNameSlug, forceRefresh, cacheTTL).value

    override suspend fun fetchDestinationFaqsResult(
        countryNameSlug: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<Faq>> =
        cache.cachedListResult(destinationFaqsKey(countryNameSlug), forceRefresh, cacheTTL) {
            try {
                apiService.getDestinationFaqs(countryNameSlug = countryNameSlug).faqs
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(FAQS_KEY_PREFIX)
    }

    private fun destinationFaqsKey(countryNameSlug: String): String =
        "${FAQS_KEY_PREFIX}destination_$countryNameSlug"
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
        const val FAQS_KEY_PREFIX = "faqs_"
    }
}
