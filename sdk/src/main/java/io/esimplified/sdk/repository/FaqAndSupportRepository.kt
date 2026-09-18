package io.esimplified.sdk.repository

import io.esimplified.sdk.model.ContentDocument
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

    suspend fun fetchTerms(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): ContentDocument? = fetchTermsResult(language, forceRefresh, cacheTTL).value

    suspend fun fetchPrivacy(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): ContentDocument? = fetchPrivacyResult(language, forceRefresh, cacheTTL).value

    suspend fun fetchFaqs(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): ContentDocument? = fetchFaqsResult(language, forceRefresh, cacheTTL).value
    // endregion

    // region Result reads
    suspend fun fetchDestinationFaqsResult(
        countryNameSlug: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): RepositoryResult<List<Faq>> =
        RepositoryResult(fetchDestinationFaqs(countryNameSlug, forceRefresh, cacheTTL))

    suspend fun fetchTermsResult(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): RepositoryResult<ContentDocument?>

    suspend fun fetchPrivacyResult(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): RepositoryResult<ContentDocument?>

    suspend fun fetchFaqsResult(
        language: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = FAQS_TTL,
    ): RepositoryResult<ContentDocument?>
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val FAQS_TTL: Duration = 86400.seconds
    }
}
