package io.esimplified.sdk.network

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SdkCache(
    private val defaultTtl: Duration = DEFAULT_TTL,
    private val isEnabled: Boolean = true,
) {

    internal class Entry(val data: Any, val expiresAt: Long) {
        val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
    }

    internal val store = ConcurrentHashMap<String, Entry>()
    private val lock = Any()
    private var invalidations = 0L

    val invalidationCount: Long
        get() = synchronized(lock) { invalidations }

    // region Reads
    inline fun <reified T : Any> get(key: String): T? {
        val entry = store[key] ?: return null
        if (entry.isExpired) return null
        return entry.data as? T
    }

    inline fun <reified T : Any> getExpired(key: String): T? = store[key]?.data as? T
    // endregion

    // region Writes
    fun set(key: String, value: Any, ttl: Duration? = null) {
        val resolvedTtl = if (isEnabled) ttl ?: defaultTtl else Duration.ZERO
        store[key] = Entry(value, System.currentTimeMillis() + resolvedTtl.inWholeMilliseconds)
    }

    fun setUnlessInvalidatedSince(invalidationCount: Long, key: String, value: Any, ttl: Duration? = null) {
        synchronized(lock) {
            if (invalidations == invalidationCount) set(key, value, ttl)
        }
    }

    fun remove(key: String) {
        synchronized(lock) {
            invalidations++
            store.remove(key)
        }
    }

    fun removeWithPrefix(prefix: String) {
        synchronized(lock) {
            invalidations++
            val keysToRemove = store.keys.filter { it.startsWith(prefix) }
            keysToRemove.forEach { store.remove(it) }
        }
    }

    fun clear() {
        synchronized(lock) {
            invalidations++
            store.clear()
        }
    }
    // endregion

    internal companion object {
        val DEFAULT_TTL: Duration = 3600.seconds
    }
}
