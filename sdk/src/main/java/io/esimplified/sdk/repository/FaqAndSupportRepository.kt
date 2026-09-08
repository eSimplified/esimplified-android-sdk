package io.esimplified.sdk.repository

import io.esimplified.sdk.model.Faq
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface FaqAndSupportRepository {

    // region Reads
    suspend fun fetchDestinationFaqs(
        countryNameSlug: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): List<Faq>
    // endregion

    // region Result reads
    suspend fun fetchDestinationFaqsResult(
        countryNameSlug: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): RepositoryResult<List<Faq>> =
        RepositoryResult(fetchDestinationFaqs(countryNameSlug, forceRefresh, cacheTTL))
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val FAQS_TTL: Duration = 86400.seconds
    }
}
