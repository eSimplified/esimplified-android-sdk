package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.Localization
import io.esimplified.sdk.model.LocalizationInclude
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.LocalizationRepository
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedResult
import kotlin.time.Duration

internal class LocalizationRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : LocalizationRepository {

    // region Localization
    override suspend fun fetchLocalizationResult(
        include: LocalizationInclude?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<Localization> {
        val key = "$LOCALIZATION_KEY_PREFIX${include?.value ?: ALL_KEY}"
        val result = cache.cachedResult<Localization>(key, forceRefresh, cacheTTL) {
            apiService.getLocalization(include?.value)
        }
        return RepositoryResult(
            value = result.value ?: Localization(),
            isStale = result.isStale,
            failure = result.failure,
        )
    }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(LOCALIZATION_KEY_PREFIX)
    }
    // endregion

    private companion object {
        const val LOCALIZATION_KEY_PREFIX = "localization_"
        const val ALL_KEY = "all"
    }
}
