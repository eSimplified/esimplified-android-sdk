package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.StoreReviewRepositoryImpl
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

class StoreReviewRepositoryImplTest {

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
    fun `the store review is requested from the reviews endpoint with the store_review type`() =
        runTest {
            enqueueStoreReview()

            StoreReviewRepositoryImpl(apiService, cache).fetchStoreReview()

            assertEquals("/api/v2/reviews/?type=store_review", mockWebServer.takeRequest().path)
        }
    // endregion

    // region Fresh cache hits
    @Test
    fun `a fresh cached entry is served without touching the network`() = runTest {
        enqueueStoreReview()
        val repo = StoreReviewRepositoryImpl(apiService, cache)

        val first = repo.fetchStoreReview()
        val second = repo.fetchStoreReview()

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals("KnowRoaming", second.storeName)
        assertEquals(4.5, second.rating!!, 0.001)
    }

    @Test
    fun `the store review is cached under the exact store_review key`() = runTest {
        enqueueStoreReview()

        StoreReviewRepositoryImpl(apiService, cache).fetchStoreReview()

        assertTrue(cache.store.containsKey("store_review"))
        assertEquals(1, cache.store.size)
    }

    @Test
    fun `a fresh cached entry reports itself as neither stale nor failed`() = runTest {
        enqueueStoreReview()
        val repo = StoreReviewRepositoryImpl(apiService, cache)
        repo.fetchStoreReview()

        val result = repo.fetchStoreReviewResult()

        assertEquals(1, mockWebServer.requestCount)
        assertFalse(result.isStale)
        assertFalse(result.didFail)
        assertNull(result.failure)
    }
    // endregion

    // region Force refresh
    @Test
    fun `forceRefresh bypasses a fresh cache entry`() = runTest {
        enqueueStoreReview()
        enqueueStoreReview(verdict = "Great")
        val repo = StoreReviewRepositoryImpl(apiService, cache)

        val cached = repo.fetchStoreReview()
        val refreshed = repo.fetchStoreReview(forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Excellent", cached.verdict)
        assertEquals("Great", refreshed.verdict)
    }
    // endregion

    // region Stale fallback
    @Test
    fun `a failed fetch falls back to expired data and reports it as stale`() = runTest {
        enqueueStoreReview()
        val repo = StoreReviewRepositoryImpl(apiService, cache)
        repo.fetchStoreReview(cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchStoreReviewResult()

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Excellent", result.value?.verdict)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `the plain read serves expired data instead of throwing when the fetch fails`() = runTest {
        enqueueStoreReview()
        val repo = StoreReviewRepositoryImpl(apiService, cache)
        repo.fetchStoreReview(cacheTTL = Duration.ZERO)

        enqueueServerError()

        assertEquals("Excellent", repo.fetchStoreReview().verdict)
    }

    @Test
    fun `a failed fetch with no cached data throws from the plain read`() = runTest {
        enqueueServerError()
        val repo = StoreReviewRepositoryImpl(apiService, cache)

        var thrown: Throwable? = null
        try {
            repo.fetchStoreReview()
        } catch (error: Throwable) {
            thrown = error
        }

        assertNotNull(thrown)
    }

    @Test
    fun `a failed fetch with no cached data reports a null value and a failure`() = runTest {
        enqueueServerError()
        val repo = StoreReviewRepositoryImpl(apiService, cache)

        val result = repo.fetchStoreReviewResult()

        assertNull(result.value)
        assertFalse(result.isStale)
        assertTrue(result.didFail)
    }
    // endregion

    // region Payload
    @Test
    fun `reviews and stats survive the round trip through the cache`() = runTest {
        enqueueStoreReview()
        val repo = StoreReviewRepositoryImpl(apiService, cache)
        repo.fetchStoreReview()

        val cached = repo.fetchStoreReview()

        assertEquals(1, cached.reviews?.size)
        assertEquals("Alice", cached.reviews?.first()?.author?.name)
        assertEquals(5, cached.reviews?.first()?.rating)
        assertEquals("store_review", cached.reviews?.first()?.type)
        assertEquals(1000, cached.stats?.company?.reviewCount)
        assertEquals("4.5", cached.stats?.company?.averageRating)
        assertEquals(200, cached.stats?.ratings?.four)
        assertEquals(800, cached.stats?.ratings?.five)
    }

    @Test
    fun `a summary only payload without reviews or stats still decodes`() = runTest {
        enqueueJson(
            """
            {
              "store_name": "KnowRoaming",
              "review_count": 1000,
              "results_count": 1000,
              "verdict": "Excellent",
              "average_rating": 4.5
            }
            """.trimIndent()
        )

        val review = StoreReviewRepositoryImpl(apiService, cache).fetchStoreReview()

        assertEquals("KnowRoaming", review.storeName)
        assertNull(review.reviews)
        assertNull(review.stats)
    }
    // endregion

    // region Invalidation
    @Test
    fun `StoreReview invalidateCache removes the exact key and sweeps no prefix`() = runTest {
        cache.set("store_review", "review")
        cache.set("store_review_extra", "other")
        cache.set("packages_rating", "legacy")
        cache.set("countries_all", listOf("ZA"))

        StoreReviewRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<String>("store_review"))
        assertNotNull(cache.getExpired<String>("store_review_extra"))
        assertNotNull(cache.getExpired<String>("packages_rating"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
        assertEquals(0, mockWebServer.requestCount)
    }
    // endregion

    // region Ttl
    @Test
    fun `the default store review ttl is one day`() {
        assertEquals(86400.seconds, StoreReviewRepository.STORE_REVIEW_TTL)
    }

    @Test
    fun `an explicit cacheTTL overrides the cache default`() = runTest {
        enqueueStoreReview()

        StoreReviewRepositoryImpl(apiService, cache).fetchStoreReview(cacheTTL = 5.seconds)

        val entry = cache.store["store_review"]
        assertNotNull(entry)
        assertTrue(entry!!.expiresAt - System.currentTimeMillis() <= 5_000)
    }
    // endregion

    private fun enqueueStoreReview(verdict: String = "Excellent") {
        enqueueJson(
            """
            {
              "store_name": "KnowRoaming",
              "review_count": 1000,
              "results_count": 1000,
              "verdict": "$verdict",
              "average_rating": 4.5,
              "reviews": [
                {
                  "type": "store_review",
                  "type_label": "Store review",
                  "rating": 5,
                  "title": "Great",
                  "comments": "Worked well",
                  "author": { "name": "Alice", "location": "Cape Town" },
                  "date_created": "2026-01-01",
                  "time_ago": "2 days ago",
                  "sku": "SKU-1"
                }
              ],
              "stats": {
                "company": { "review_count": 1000, "average_rating": "4.5" },
                "ratings": { "4": 200, "5": 800 }
              }
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
