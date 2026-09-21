package io.esimplified.sdk.repository

import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.network.SdkError
import kotlinx.coroutines.CancellationException
import io.esimplified.sdk.SdkLog
import kotlin.time.Duration

internal fun String.cacheKeyFamily(): String = substringBefore('_') + "_"

// region Cached reads
internal suspend inline fun <reified T : Any> SdkCache.cachedResult(
    key: String,
    forceRefresh: Boolean,
    ttl: Duration,
    fetch: () -> T,
): RepositoryResult<T?> {
    if (!forceRefresh) {
        val fresh = get<T>(key)
        if (fresh != null) return RepositoryResult(fresh)
    }
    return try {
        val fetched = fetch()
        set(key, fetched, ttl)
        RepositoryResult(fetched)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        val expired = getExpired<T>(key)
        SdkLog.w("Cached read failed for ${key.cacheKeyFamily()}, serving cached=${expired != null}", error)
        RepositoryResult(expired, isStale = expired != null, failure = error.asSdkError())
    }
}

internal suspend inline fun <reified T : Any> SdkCache.cachedListResult(
    key: String,
    forceRefresh: Boolean,
    ttl: Duration,
    fetch: () -> List<T>,
): RepositoryResult<List<T>> {
    val result = cachedResult<List<T>>(key, forceRefresh, ttl, fetch)
    val value = result.value ?: emptyList()
    return RepositoryResult(
        value = value,
        isStale = result.isStale && value.isNotEmpty(),
        failure = result.failure,
    )
}
// endregion

// region Unwrapping
internal fun <T : Any> RepositoryResult<T?>.valueOrThrow(): T {
    val cached = value
    if (cached != null) return cached
    throw failure.originalOrSelf()
}

internal fun <T> RepositoryResult<List<T>>.listOrThrow(): List<T> {
    val failed = failure
    if (value.isNotEmpty() || failed == null) return value
    throw failed.originalOrSelf()
}

// endregion

// region Combining
internal fun <T> combineResults(
    first: RepositoryResult<List<T>>,
    second: RepositoryResult<List<T>>,
): RepositoryResult<List<T>> = RepositoryResult(
    value = first.value + second.value,
    isStale = first.isStale || second.isStale,
    failure = first.failure ?: second.failure,
)
// endregion
