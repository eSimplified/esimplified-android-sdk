package io.esimplified.sdk.repository

import io.esimplified.sdk.model.Localization
import io.esimplified.sdk.model.LocalizationInclude
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface LocalizationRepository {

    // region Reads
    suspend fun fetchLocalization(
        include: LocalizationInclude? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = LOCALIZATION_TTL,
    ): Localization = fetchLocalizationResult(include, forceRefresh, cacheTTL).value
    // endregion

    // region Result reads
    suspend fun fetchLocalizationResult(
        include: LocalizationInclude? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = LOCALIZATION_TTL,
    ): RepositoryResult<Localization>
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val LOCALIZATION_TTL: Duration = 3600.seconds
    }
}
