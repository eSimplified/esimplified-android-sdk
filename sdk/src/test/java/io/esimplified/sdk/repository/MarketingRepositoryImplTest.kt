package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.MarketingRepositoryImpl
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

class MarketingRepositoryImplTest {

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
    fun `promos are requested from the marketing path with its trailing slash`() = runTest {
        enqueuePromos()

        repository().fetchPromos("en")

        assertEquals("/api/v2/marketing/", mockWebServer.takeRequest().path)
    }

    @Test
    fun `the language never reaches the request`() = runTest {
        enqueuePromos()

        repository().fetchPromos("es")

        val request = mockWebServer.takeRequest()
        assertEquals("/api/v2/marketing/", request.path)
        assertNull(request.getHeader("language"))
    }
    // endregion

    // region Fresh cache hits
    @Test
    fun `a fresh cached entry is served without touching the network`() = runTest {
        enqueuePromos()
        val repo = repository()

        val first = repo.fetchPromos("en")
        val second = repo.fetchPromos("en")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals("Got Kreds? Save on data.", second.single().title)
    }

    @Test
    fun `a fresh cached entry reports itself as neither stale nor failed`() = runTest {
        enqueuePromos()
        val repo = repository()
        repo.fetchPromos("en")

        val result = repo.fetchPromosResult("en")

        assertEquals(1, mockWebServer.requestCount)
        assertFalse(result.isStale)
        assertFalse(result.didFail)
        assertNull(result.failure)
    }

    @Test
    fun `each language caches separately`() = runTest {
        enqueuePromos()
        enqueuePromos(title = "Hola")
        val repo = repository()

        repo.fetchPromos("en")
        repo.fetchPromos("es")

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("marketing_en"))
        assertTrue(cache.store.containsKey("marketing_es"))
    }
    // endregion

    // region Stale fallback
    @Test
    fun `a failed fetch falls back to the expired carousel and reports it as stale`() = runTest {
        enqueuePromos()
        val repo = repository()
        repo.fetchPromos("en", cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchPromosResult("en")

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Got Kreds? Save on data.", result.value.single().title)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `a failed fetch with no cached carousel returns an empty list and the failure`() = runTest {
        enqueueServerError()

        val result = repository().fetchPromosResult("en")

        assertTrue(result.value.isEmpty())
        assertFalse(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }
    // endregion

    // region Cache invalidation
    @Test
    fun `invalidateCache drops every language under the marketing prefix`() = runTest {
        enqueuePromos()
        enqueuePromos(title = "Hola")
        val repo = repository()
        repo.fetchPromos("en")
        repo.fetchPromos("es")

        repo.invalidateCache()

        assertFalse(cache.store.containsKey("marketing_en"))
        assertFalse(cache.store.containsKey("marketing_es"))
    }
    // endregion

    private fun repository() = MarketingRepositoryImpl(apiService, cache)

    private fun enqueuePromos(title: String = "Got Kreds? Save on data.") {
        enqueueJson(
            """
            {
              "promos": [
                {
                  "slug": "https://knowroaming.vercel.app/kreds",
                  "title": "$title",
                  "color": "#1E1E1E",
                  "image": "https://cdn.sanity.io/promo.webp",
                  "content": "That data plan for your next trip?",
                  "cta_heading": "Your Kreds are ready to be spent.",
                  "cta_text": "Check your balance",
                  "faq_heading": "Buy, and earn.",
                  "faqs": [{ "question": "How?", "answer": "Buy." }],
                  "slider_image": "https://cdn.sanity.io/slider.webp",
                  "slider_heading": null,
                  "slider_subheading": null
                }
              ]
            }
            """.trimIndent()
        )
    }

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
