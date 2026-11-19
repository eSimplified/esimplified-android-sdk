package io.esimplified.sdk.repository

import io.esimplified.sdk.network.SdkCache
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedReadInvalidationTest {

    private val cache = SdkCache()

    @Test
    fun `a read that started before an invalidation does not overwrite what was cached after it`() = runTest {
        val slowResponse = CompletableDeferred<String>()
        val slowRead = async { cache.cachedResult<String>(KEY, forceRefresh = false, ttl = TTL) { slowResponse.await() } }
        testScheduler.runCurrent()

        cache.removeWithPrefix(PREFIX)
        cache.cachedResult<String>(KEY, forceRefresh = false, ttl = TTL) { "EUR" }

        slowResponse.complete("USD")

        assertEquals("USD", slowRead.await().value)
        assertEquals("EUR", cache.get<String>(KEY))
    }

    @Test
    fun `a read that started before a full clear does not put its result back`() = runTest {
        val slowResponse = CompletableDeferred<String>()
        val slowRead = async { cache.cachedResult<String>(KEY, forceRefresh = false, ttl = TTL) { slowResponse.await() } }
        testScheduler.runCurrent()

        cache.clear()
        slowResponse.complete("USD")
        slowRead.await()

        assertNull(cache.get<String>(KEY))
    }

    @Test
    fun `removing the key itself also stops the earlier read from putting it back`() = runTest {
        val slowResponse = CompletableDeferred<String>()
        val slowRead = async { cache.cachedResult<String>(KEY, forceRefresh = false, ttl = TTL) { slowResponse.await() } }
        testScheduler.runCurrent()

        cache.remove(KEY)
        slowResponse.complete("USD")
        slowRead.await()

        assertNull(cache.get<String>(KEY))
    }

    @Test
    fun `invalidating another repository's entries leaves the read cached`() = runTest {
        val slowResponse = CompletableDeferred<String>()
        val slowRead = async { cache.cachedResult<String>(KEY, forceRefresh = false, ttl = TTL) { slowResponse.await() } }
        testScheduler.runCurrent()

        cache.remove("order_1234")
        cache.removeWithPrefix("esims_")
        slowResponse.complete("USD")
        slowRead.await()

        assertEquals("USD", cache.get<String>(KEY))
    }

    @Test
    fun `a read with no invalidation in between is cached as before`() = runTest {
        cache.cachedResult<String>(KEY, forceRefresh = false, ttl = TTL) { "USD" }

        assertEquals("USD", cache.get<String>(KEY))
    }

    private companion object {
        const val PREFIX = "packages_"
        const val KEY = "packages_fr"
        val TTL = 60.seconds
    }
}
