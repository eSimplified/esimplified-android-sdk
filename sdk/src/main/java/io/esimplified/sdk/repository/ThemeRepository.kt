package io.esimplified.sdk.repository

import io.esimplified.sdk.model.ThemeDestination
import io.esimplified.sdk.model.ThemePage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface ThemeRepository {

    // region Reads
    suspend fun fetchPageTheme(
        page: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = THEME_TTL,
    ): ThemePage?

    suspend fun fetchDestinationTheme(
        countryCode: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = THEME_TTL,
    ): ThemeDestination?
    // endregion

    // region Result reads
    suspend fun fetchPageThemeResult(
        page: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = THEME_TTL,
    ): RepositoryResult<ThemePage?> =
        RepositoryResult(fetchPageTheme(page, forceRefresh, cacheTTL))

    suspend fun fetchDestinationThemeResult(
        countryCode: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = THEME_TTL,
    ): RepositoryResult<ThemeDestination?> =
        RepositoryResult(fetchDestinationTheme(countryCode, forceRefresh, cacheTTL))
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val THEME_TTL: Duration = 3600.seconds
    }
}
