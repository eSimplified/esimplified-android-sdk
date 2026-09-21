package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.ContentDocument
import io.esimplified.sdk.model.Faq
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.FaqAndSupportRepository
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import kotlin.time.Duration

internal class FaqAndSupportRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : FaqAndSupportRepository {

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
            apiService.getDestinationFaqs(countryNameSlug = countryNameSlug).faqs
        }
    // endregion

    // region Terms
    override suspend fun fetchTermsResult(
        language: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<ContentDocument?> =
        fetchDocumentResult("$TERMS_KEY_PREFIX$language", forceRefresh, cacheTTL) {
            apiService.getTerms()
        }
    // endregion

    // region Privacy
    override suspend fun fetchPrivacyResult(
        language: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<ContentDocument?> =
        fetchDocumentResult("$PRIVACY_KEY_PREFIX$language", forceRefresh, cacheTTL) {
            apiService.getPrivacy()
        }
    // endregion

    // region FAQs
    override suspend fun fetchFaqsResult(
        language: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<ContentDocument?> =
        fetchDocumentResult("$FAQS_KEY_PREFIX$language", forceRefresh, cacheTTL) {
            apiService.getFaqs()
        }
    // endregion

    // region Content document
    private suspend fun fetchDocumentResult(
        cacheKey: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        fetch: suspend () -> ContentDocument,
    ): RepositoryResult<ContentDocument?> =
        cache.cachedResult(cacheKey, forceRefresh, cacheTTL) {
            fetch()
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        CACHE_KEY_PREFIXES.forEach { cache.removeWithPrefix(it) }
    }

    private fun destinationFaqsKey(countryNameSlug: String): String =
        "${FAQS_KEY_PREFIX}destination_$countryNameSlug"
    // endregion

    private companion object {
        const val FAQS_KEY_PREFIX = "faqs_"
        const val TERMS_KEY_PREFIX = "terms_"
        const val PRIVACY_KEY_PREFIX = "privacy_"
        val CACHE_KEY_PREFIXES = listOf(FAQS_KEY_PREFIX, TERMS_KEY_PREFIX, PRIVACY_KEY_PREFIX)
    }
}
