package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.LocalizationInclude
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.LocalizationRepositoryImpl
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

class LocalizationRepositoryImplTest {

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

    // region Requests
    @Test
    fun `currencies are requested with include=currencies and decode to their codes`() = runTest {
        enqueueCurrencies()

        val localization = repository().fetchLocalization(LocalizationInclude.CURRENCIES)

        assertEquals("/api/v2/localization/?include=currencies", mockWebServer.takeRequest().path)
        assertEquals(listOf("AED", "USD"), localization.currencies.map { it.code })
        assertEquals("$", localization.currencies.last().symbol)
        assertEquals("", localization.defaultCurrency)
        assertTrue(localization.languages.isEmpty())
        assertNull(localization.defaultLanguage)
    }

    @Test
    fun `languages are requested with include=languages and decode with no currencies`() = runTest {
        enqueueLanguages()

        val localization = repository().fetchLocalization(LocalizationInclude.LANGUAGES)

        assertEquals("/api/v2/localization/?include=languages", mockWebServer.takeRequest().path)
        assertEquals(listOf("en", "ar"), localization.languages.map { it.code })
        assertEquals("en", localization.defaultLanguage)
        assertTrue(localization.currencies.isEmpty())
        assertNull(localization.defaultCurrency)
    }

    @Test
    fun `no include sends no query`() = runTest {
        enqueueCurrencies()

        repository().fetchLocalization()

        assertEquals("/api/v2/localization/", mockWebServer.takeRequest().path)
    }

    @Test
    fun `a currency that carries only its code still decodes`() = runTest {
        enqueueJson("""{"currencies":[{"code":"XYZ"}]}""")

        val currency = repository().fetchLocalization(LocalizationInclude.CURRENCIES).currencies.single()

        assertEquals("XYZ", currency.code)
        assertNull(currency.name)
        assertNull(currency.symbol)
    }
    // endregion

    // region Cache
    @Test
    fun `a fresh cached entry is served without touching the network`() = runTest {
        enqueueCurrencies()
        val repo = repository()

        repo.fetchLocalization(LocalizationInclude.CURRENCIES)
        val result = repo.fetchLocalizationResult(LocalizationInclude.CURRENCIES)

        assertEquals(1, mockWebServer.requestCount)
        assertFalse(result.isStale)
        assertFalse(result.didFail)
    }

    @Test
    fun `forceRefresh goes back to the network`() = runTest {
        enqueueCurrencies()
        enqueueCurrencies()
        val repo = repository()

        repo.fetchLocalization(LocalizationInclude.CURRENCIES)
        repo.fetchLocalization(LocalizationInclude.CURRENCIES, forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `currencies and languages cache separately`() = runTest {
        enqueueCurrencies()
        enqueueLanguages()
        val repo = repository()

        repo.fetchLocalization(LocalizationInclude.CURRENCIES)
        repo.fetchLocalization(LocalizationInclude.LANGUAGES)

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("localization_currencies"))
        assertTrue(cache.store.containsKey("localization_languages"))
    }

    @Test
    fun `invalidateCache drops the cached lists`() = runTest {
        enqueueCurrencies()
        enqueueCurrencies()
        val repo = repository()
        repo.fetchLocalization(LocalizationInclude.CURRENCIES)

        repo.invalidateCache()
        repo.fetchLocalization(LocalizationInclude.CURRENCIES)

        assertEquals(2, mockWebServer.requestCount)
    }
    // endregion

    // region Failure
    @Test
    fun `a failed fetch falls back to the expired list and reports it as stale`() = runTest {
        enqueueCurrencies()
        val repo = repository()
        repo.fetchLocalization(LocalizationInclude.CURRENCIES, cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchLocalizationResult(LocalizationInclude.CURRENCIES)

        assertEquals(listOf("AED", "USD"), result.value.currencies.map { it.code })
        assertTrue(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `a failed fetch with nothing cached returns an empty localization and the failure`() = runTest {
        enqueueServerError()

        val result = repository().fetchLocalizationResult(LocalizationInclude.CURRENCIES)

        assertTrue(result.value.currencies.isEmpty())
        assertFalse(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }
    // endregion

    // region Helpers
    private fun repository() = LocalizationRepositoryImpl(apiService, cache)

    private fun enqueueCurrencies() {
        enqueueJson(
            """
            {"default_currency":"","currencies":[
              {"code":"AED","name":"United Arab Dirham","symbol":"AED"},
              {"code":"USD","name":"US Dollar","symbol":"$"}
            ]}
            """.trimIndent()
        )
    }

    private fun enqueueLanguages() {
        enqueueJson(
            """{"default_language":"en","languages":[{"code":"en","name":"English"},{"code":"ar","name":"Arabic"}]}"""
        )
    }

    private fun enqueueServerError() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Not found."}"""))
    }

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }
    // endregion
}
