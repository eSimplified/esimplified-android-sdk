package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.CountryRepositoryImpl
import io.esimplified.sdk.repository.impl.EsimRepositoryImpl
import io.esimplified.sdk.repository.impl.LoyaltyRepositoryImpl
import io.esimplified.sdk.repository.impl.OrdersRepositoryImpl
import io.esimplified.sdk.repository.impl.PackagesRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RepositoryCachingTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var cache: SdkCache

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        cache = SdkCache()

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // region Fresh cache hits
    @Test
    fun `a fresh cached entry is served without touching the network`() = runTest {
        enqueueCountries()
        val repo = CountryRepositoryImpl(apiService, cache)

        val first = repo.getCountries()
        val second = repo.getCountries()

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals("ZA", second.first().code)
    }

    @Test
    fun `a fresh cached entry reports itself as neither stale nor failed`() = runTest {
        enqueueCountries()
        val repo = CountryRepositoryImpl(apiService, cache)
        repo.getCountries()

        val result = repo.getCountriesResult()

        assertEquals(1, mockWebServer.requestCount)
        assertFalse(result.isStale)
        assertFalse(result.didFail)
        assertNull(result.failure)
    }

    @Test
    fun `each eSIM list query parameter combination caches separately`() = runTest {
        enqueueEmptyEsims()
        enqueueEmptyEsims()
        val repo = EsimRepositoryImpl(apiService, cache)

        repo.getActiveEsims()
        repo.getArchivedEsims()
        repo.getActiveEsims()
        repo.getArchivedEsims()

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("esims_false_legacytrue_primaryany_qrfalse"))
        assertTrue(cache.store.containsKey("esims_true_legacytrue_primaryany_qrfalse"))
    }
    // endregion

    // region Force refresh
    @Test
    fun `forceRefresh bypasses a fresh cache entry`() = runTest {
        enqueueCountries()
        enqueueCountries("GB")
        val repo = CountryRepositoryImpl(apiService, cache)

        val cached = repo.getCountries()
        val refreshed = repo.getCountries(forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("ZA", cached.first().code)
        assertEquals("GB", refreshed.first().code)
    }

    @Test
    fun `forceRefresh writes the new value back into the cache`() = runTest {
        enqueueCountries()
        enqueueCountries("GB")
        val repo = CountryRepositoryImpl(apiService, cache)
        repo.getCountries()

        repo.getCountries(forceRefresh = true)
        val afterRefresh = repo.getCountries()

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("GB", afterRefresh.first().code)
    }
    // endregion

    // region Stale fallback
    @Test
    fun `a failed fetch falls back to expired data and reports it as stale`() = runTest {
        enqueueCountries()
        val repo = CountryRepositoryImpl(apiService, cache)
        repo.getCountries(cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.getCountriesResult()

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("ZA", result.value.first().code)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `the plain read serves expired data instead of throwing when the fetch fails`() = runTest {
        enqueueCountries()
        val repo = CountryRepositoryImpl(apiService, cache)
        repo.getCountries(cacheTTL = Duration.ZERO)

        enqueueServerError()
        val countries = repo.getCountries()

        assertEquals("ZA", countries.first().code)
    }

    @Test
    fun `a failed fetch with no cached data throws from the plain read`() = runTest {
        enqueueServerError()
        val repo = CountryRepositoryImpl(apiService, cache)

        var thrown: Throwable? = null
        try {
            repo.getCountries()
        } catch (error: Throwable) {
            thrown = error
        }

        assertNotNull(thrown)
    }

    @Test
    fun `a failed fetch with no cached data reports failure without staleness`() = runTest {
        enqueueServerError()
        val repo = CountryRepositoryImpl(apiService, cache)

        val result = repo.getCountriesResult()

        assertTrue(result.value.isEmpty())
        assertFalse(result.isStale)
        assertTrue(result.didFail)
    }

    @Test
    fun `an expired single object read falls back with the failure attached`() = runTest {
        enqueueLoyaltyBalance()
        val repo = LoyaltyRepositoryImpl(apiService, cache)
        repo.getLoyaltyBalance(cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.getLoyaltyBalanceResult()

        assertEquals(1500, result.value?.totalLoyaltyPoints)
        assertTrue(result.isStale)
        assertNotNull(result.failure)
    }

    @Test
    fun `a single object read without cached data surfaces a null value and a failure`() = runTest {
        enqueueServerError()
        val repo = LoyaltyRepositoryImpl(apiService, cache)

        val result = repo.getLoyaltyBalanceResult()

        assertNull(result.value)
        assertFalse(result.isStale)
        assertTrue(result.didFail)
    }
    // endregion

    // region Invalidation
    @Test
    fun `Esim invalidateCache clears both esims and esim_details but leaves countries`() = runTest {
        cache.set("esims_false_legacytrue_primaryany_qrfalse", listOf("active"))
        cache.set("esims_true_legacytrue_primaryany_qrfalse", listOf("archived"))
        cache.set("esim_details_8931_qrfalse", "details")
        cache.set("countries_all", listOf("ZA"))
        cache.set("countries_by_ZA__", listOf("ZA"))

        EsimRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<List<String>>("esims_false_legacytrue_primaryany_qrfalse"))
        assertNull(cache.getExpired<List<String>>("esims_true_legacytrue_primaryany_qrfalse"))
        assertNull(cache.getExpired<String>("esim_details_8931_qrfalse"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
        assertNotNull(cache.getExpired<List<String>>("countries_by_ZA__"))
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `Orders invalidateCache clears both orders and order keys`() = runTest {
        cache.set("orders_none", listOf("a"))
        cache.set("orders_true", listOf("b"))
        cache.set("order_uuid-1", "detail")
        cache.set("countries_all", listOf("ZA"))

        OrdersRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<List<String>>("orders_none"))
        assertNull(cache.getExpired<List<String>>("orders_true"))
        assertNull(cache.getExpired<String>("order_uuid-1"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
    }

    @Test
    fun `Packages invalidateCache clears both packages and check_stock keys`() = runTest {
        cache.set("packages_ZA_south-africa_za", listOf("p"))
        cache.set("packages_topup_8931", listOf("t"))
        cache.set("check_stock_42", "stock")
        cache.set("countries_all", listOf("ZA"))

        PackagesRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<List<String>>("packages_ZA_south-africa_za"))
        assertNull(cache.getExpired<List<String>>("packages_topup_8931"))
        assertNull(cache.getExpired<String>("check_stock_42"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
    }

    @Test
    fun `Country invalidateCache clears every countries key`() = runTest {
        cache.set("countries_all", listOf("ZA"))
        cache.set("countries_by_ZA__", listOf("ZA"))
        cache.set("esims_false_legacytrue_primaryany_qrfalse", listOf("active"))

        CountryRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<List<String>>("countries_all"))
        assertNull(cache.getExpired<List<String>>("countries_by_ZA__"))
        assertNotNull(cache.getExpired<List<String>>("esims_false_legacytrue_primaryany_qrfalse"))
    }

    @Test
    fun `updating an eSIM drops that detail entry and every eSIM list`() = runTest {
        cache.set("esims_false_legacytrue_primaryany_qrfalse", listOf("active"))
        cache.set("esim_details_8931_qrfalse", "details")
        cache.set("esim_details_9999_qrfalse", "other")
        cache.set("countries_all", listOf("ZA"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"message":"eSIM updated successfully"}"""))

        EsimRepositoryImpl(apiService, cache).updateEsim(iccid = "8931", name = "Trip")

        assertNull(cache.getExpired<List<String>>("esims_false_legacytrue_primaryany_qrfalse"))
        assertNull(cache.getExpired<String>("esim_details_8931_qrfalse"))
        assertNotNull(cache.getExpired<String>("esim_details_9999_qrfalse"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
    }
    // endregion

    // region Ttl
    @Test
    fun `an explicit cacheTTL overrides the cache default`() = runTest {
        enqueueCountries()
        val repo = CountryRepositoryImpl(apiService, cache)

        repo.getCountries(cacheTTL = 5.seconds)

        val entry = cache.store["countries_all"]
        assertNotNull(entry)
        val remaining = entry!!.expiresAt - System.currentTimeMillis()
        assertTrue(remaining <= 5_000)
    }
    // endregion

    private fun enqueueCountries(code: String = "ZA") {
        enqueueJson(
            """{"count":1,"results":[{"country_name":"Country $code","country_code":"$code",""" +
                """"country_flag":"flag","country_flag_css":"css","country_name_slug":"slug-$code"}]}"""
        )
    }

    private fun enqueueEmptyEsims() = enqueueJson("""{"count":0,"results":[]}""")

    private fun enqueueLoyaltyBalance() = enqueueJson(
        """{"total_loyalty_points":1500,"total_loyalty_points_detail":{"amount":"15.00","currency":{"symbol":"$","iso":"USD"}}}"""
    )

    private fun enqueueServerError() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}"""))
    }

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }
}
