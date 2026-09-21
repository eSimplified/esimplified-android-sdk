package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.ThemeDestination
import io.esimplified.sdk.model.ThemePage
import io.esimplified.sdk.model.ThemeResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.ThemeRepository
import io.esimplified.sdk.repository.apiRead
import io.esimplified.sdk.repository.asSdkError
import io.esimplified.sdk.repository.originalOrSelf
import kotlin.time.Duration
import kotlinx.coroutines.CancellationException

internal class ThemeRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : ThemeRepository {

    // region Page theme
    override suspend fun fetchPageTheme(
        page: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): ThemePage? = fetchPageThemeResult(page, forceRefresh, cacheTTL).valueOrThrowFailure()

    override suspend fun fetchPageThemeResult(
        page: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<ThemePage?> {
        val key = "${THEME_KEY_PREFIX}page_$page"
        if (!forceRefresh) {
            val fresh = cache.get<ThemePage>(key)
            if (fresh != null) return RepositoryResult(fresh)
        }
        return try {
            val theme = fetchTheme("/$page").pages[page]
            if (theme != null) cache.set(key, theme, cacheTTL)
            RepositoryResult(theme)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val expired = cache.getExpired<ThemePage>(key)
            RepositoryResult(expired, isStale = expired != null, failure = error.asSdkError())
        }
    }
    // endregion

    // region Destination theme
    override suspend fun fetchDestinationTheme(
        countryCode: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): ThemeDestination? =
        fetchDestinationThemeResult(countryCode, forceRefresh, cacheTTL).valueOrThrowFailure()

    override suspend fun fetchDestinationThemeResult(
        countryCode: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<ThemeDestination?> {
        val code = countryCode.lowercase()
        val key = "${THEME_KEY_PREFIX}destination_$code"
        if (!forceRefresh) {
            val fresh = cache.get<ThemeDestination>(key)
            if (fresh != null) return RepositoryResult(fresh)
        }
        return try {
            val theme = fetchTheme("/destinations/$code")
                .destinations
                .values
                .firstOrNull { it.countryCode?.lowercase() == code }
            if (theme != null) cache.set(key, theme, cacheTTL)
            RepositoryResult(theme)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val expired = cache.getExpired<ThemeDestination>(key)
            RepositoryResult(expired, isStale = expired != null, failure = error.asSdkError())
        }
    }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(THEME_KEY_PREFIX)
    }
    // endregion

    private suspend fun fetchTheme(url: String): ThemeResponse =
        apiRead { apiService.getTheme(url = url) }

    private fun <T : Any> RepositoryResult<T?>.valueOrThrowFailure(): T? {
        val cached = value
        if (cached != null) return cached
        val failed = failure ?: return null
        throw failed.originalOrSelf()
    }

    private companion object {
        const val THEME_KEY_PREFIX = "theme_"
    }
}
