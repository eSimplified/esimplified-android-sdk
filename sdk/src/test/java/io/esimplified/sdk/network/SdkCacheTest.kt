package io.esimplified.sdk.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class SdkCacheTest {

    // region Reads
    @Test
    fun `get returns a value stored within its ttl`() {
        val cache = SdkCache()
        cache.set("countries_", listOf("ZA", "GB"))

        assertEquals(listOf("ZA", "GB"), cache.get<List<String>>("countries_"))
    }

    @Test
    fun `get returns null for an unknown key`() {
        val cache = SdkCache()

        assertNull(cache.get<String>("missing"))
    }

    @Test
    fun `get returns null once the entry has expired`() {
        val cache = SdkCache()
        cache.set("faqs_", "cached", ttl = Duration.ZERO)

        assertNull(cache.get<String>("faqs_"))
    }

    @Test
    fun `get returns null when the cached value is a different type`() {
        val cache = SdkCache()
        cache.set("kreds_balance", 42)

        assertNull(cache.get<String>("kreds_balance"))
        assertEquals(42, cache.get<Int>("kreds_balance"))
    }

    @Test
    fun `getExpired returns the value after it has expired`() {
        val cache = SdkCache()
        cache.set("esims_false_legacyfalse_primarynil", "stale payload", ttl = Duration.ZERO)

        assertNull(cache.get<String>("esims_false_legacyfalse_primarynil"))
        assertEquals("stale payload", cache.getExpired<String>("esims_false_legacyfalse_primarynil"))
    }

    @Test
    fun `getExpired returns the value while it is still fresh`() {
        val cache = SdkCache()
        cache.set("orders_", "fresh payload")

        assertEquals("fresh payload", cache.getExpired<String>("orders_"))
    }

    @Test
    fun `getExpired returns null for an unknown key`() {
        val cache = SdkCache()

        assertNull(cache.getExpired<String>("missing"))
    }
    // endregion

    // region Ttl
    @Test
    fun `default ttl is one hour`() {
        val cache = SdkCache()
        val before = System.currentTimeMillis()
        cache.set("packages_", "value")

        val expiresAt = cache.store.getValue("packages_").expiresAt
        assertTrue(expiresAt - before >= 3_599_000)
        assertTrue(expiresAt - before <= 3_600_000)
    }

    @Test
    fun `explicit ttl overrides the default`() {
        val cache = SdkCache()
        val before = System.currentTimeMillis()
        cache.set("store_review", "value", ttl = 60.seconds)
        val after = System.currentTimeMillis()

        val expiresAt = cache.store.getValue("store_review").expiresAt
        assertTrue(expiresAt >= before + 60_000)
        assertTrue(expiresAt <= after + 60_000)
    }

    @Test
    fun `a zero default ttl expires every entry immediately but keeps it readable as stale`() {
        val cache = SdkCache(defaultTtl = Duration.ZERO)
        cache.set("theme_", "value")

        assertNull(cache.get<String>("theme_"))
        assertEquals("value", cache.getExpired<String>("theme_"))
    }

    @Test
    fun `set overwrites an existing entry`() {
        val cache = SdkCache()
        cache.set("order_abc", "first")
        cache.set("order_abc", "second")

        assertEquals("second", cache.get<String>("order_abc"))
    }
    // endregion

    // region Removal
    @Test
    fun `remove deletes only the named key`() {
        val cache = SdkCache()
        cache.set("kreds_balance", "balance")
        cache.set("store_review", "review")

        cache.remove("kreds_balance")

        assertNull(cache.get<String>("kreds_balance"))
        assertEquals("review", cache.get<String>("store_review"))
    }

    @Test
    fun `remove on an unknown key leaves the cache untouched`() {
        val cache = SdkCache()
        cache.set("faqs_", "value")

        cache.remove("nothing_here")

        assertEquals("value", cache.get<String>("faqs_"))
        assertEquals(1, cache.store.size)
    }

    @Test
    fun `removeWithPrefix sweeps only keys carrying that prefix`() {
        val cache = SdkCache()
        cache.set("orders_page1", "orders")
        cache.set("order_uuid-1", "single order")
        cache.set("packages_all", "packages")

        cache.removeWithPrefix("orders_")

        assertNull(cache.get<String>("orders_page1"))
        assertEquals("single order", cache.get<String>("order_uuid-1"))
        assertEquals("packages", cache.get<String>("packages_all"))
    }

    @Test
    fun `removeWithPrefix on esims does not sweep esim details`() {
        val cache = SdkCache()
        cache.set("esims_false_legacyfalse_primarynil", "list")
        cache.set("esim_details_8944", "details")

        cache.removeWithPrefix("esims_")

        assertNull(cache.get<String>("esims_false_legacyfalse_primarynil"))
        assertEquals("details", cache.get<String>("esim_details_8944"))

        cache.removeWithPrefix("esim_details_")

        assertNull(cache.get<String>("esim_details_8944"))
    }

    @Test
    fun `removeWithPrefix also drops expired entries matching the prefix`() {
        val cache = SdkCache()
        cache.set("countries_en", "countries", ttl = Duration.ZERO)

        cache.removeWithPrefix("countries_")

        assertNull(cache.getExpired<String>("countries_en"))
    }

    @Test
    fun `clear empties the whole cache`() {
        val cache = SdkCache()
        cache.set("esims_all", "a")
        cache.set("orders_all", "b")
        cache.set("kreds_balance", "c")

        cache.clear()

        assertEquals(0, cache.store.size)
        assertNull(cache.get<String>("esims_all"))
        assertNull(cache.getExpired<String>("orders_all"))
        assertNull(cache.getExpired<String>("kreds_balance"))
    }
    // endregion
}
