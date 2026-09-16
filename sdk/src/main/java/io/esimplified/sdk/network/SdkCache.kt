package io.esimplified.sdk.network

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class SdkCache(private val defaultTtl: Duration = DEFAULT_TTL) {

    internal class Entry(val data: Any, val expiresAt: Long) {
        val isExpired: Boolean get() = System.currentTimeMillis() >= expiresAt
    }

    internal val store = ConcurrentHashMap<String, Entry>()

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
        val resolvedTtl = ttl ?: defaultTtl
        store[key] = Entry(value, System.currentTimeMillis() + resolvedTtl.inWholeMilliseconds)
    }

    fun remove(key: String) {
        store.remove(key)
    }

    fun removeWithPrefix(prefix: String) {
        val keysToRemove = store.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { store.remove(it) }
    }

    fun clear() {
        store.clear()
    }
    // endregion

    internal companion object {
        val DEFAULT_TTL: Duration = 3600.seconds
    }
}
