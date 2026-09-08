package io.esimplified.sdk

import io.esimplified.sdk.network.SdkCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class EsimplifiedSdkCacheTest {

    @After
    fun teardown() {
        setCache(null)
    }

    @Test
    fun `clearAllCaches empties every cached repository entry at once`() {
        val cache = SdkCache()
        cache.set("esims_false_legacyfalse_primarynil", "esims")
        cache.set("esim_details_8944", "details")
        cache.set("orders_page1", "orders")
        cache.set("kreds_balance", "balance")
        setCache(cache)

        EsimplifiedSdk.clearAllCaches()

        assertEquals(0, cache.store.size)
        assertNull(cache.getExpired<String>("esims_false_legacyfalse_primarynil"))
        assertNull(cache.getExpired<String>("esim_details_8944"))
        assertNull(cache.getExpired<String>("orders_page1"))
        assertNull(cache.getExpired<String>("kreds_balance"))
    }

    @Test
    fun `clearAllCaches before initialize does not throw`() {
        setCache(null)

        EsimplifiedSdk.clearAllCaches()
    }

    @Test
    fun `the shared cache keeps serving new entries after being cleared`() {
        val cache = SdkCache()
        cache.set("countries_", "countries")
        setCache(cache)

        EsimplifiedSdk.clearAllCaches()
        cache.set("countries_", "refetched", ttl = 60.seconds)

        assertEquals("refetched", cache.get<String>("countries_"))
    }

    @Test
    fun `cache accessor throws before initialize`() {
        setCache(null)

        val thrown = try {
            EsimplifiedSdk.cache
            null
        } catch (e: IllegalStateException) {
            e
        }

        assertTrue(thrown is IllegalStateException)
    }

    private fun setCache(cache: SdkCache?) {
        val field = EsimplifiedSdk::class.java.getDeclaredField("_cache")
        field.isAccessible = true
        field.set(EsimplifiedSdk, cache)
    }
}
