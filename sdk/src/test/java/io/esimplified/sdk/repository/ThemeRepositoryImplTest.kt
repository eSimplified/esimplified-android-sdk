package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.ThemePage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.ThemeRepositoryImpl
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

class ThemeRepositoryImplTest {

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
    fun `a page theme is requested with the page key as the url query`() = runTest {
        enqueueTheme()

        ThemeRepositoryImpl(apiService, cache).fetchPageTheme("home")

        val path = mockWebServer.takeRequest().path
        assertNotNull(path)
        assertTrue(path!!.startsWith("/api/v2/theme/"))
        assertTrue(path.contains("url=%2Fhome") || path.contains("url=/home"))
    }

    @Test
    fun `a destination theme is requested with a lowercased destinations url query`() = runTest {
        enqueueTheme()

        ThemeRepositoryImpl(apiService, cache).fetchDestinationTheme("ZA")

        val path = mockWebServer.takeRequest().path
        assertNotNull(path)
        assertTrue(path!!.contains("destinations%2Fza") || path.contains("destinations/za"))
    }
    // endregion

    // region Fresh cache hits
    @Test
    fun `a fresh cached page theme is served without touching the network`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)

        val first = repo.fetchPageTheme("home")
        val second = repo.fetchPageTheme("home")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals("#FF0000", second?.color)
        assertEquals("https://cdn/home.png", second?.featuredImage?.url)
    }

    @Test
    fun `a fresh cached destination theme is served without touching the network`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)

        val first = repo.fetchDestinationTheme("za")
        val second = repo.fetchDestinationTheme("ZA")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals("za", second?.countryCode)
    }

    @Test
    fun `each page key caches separately`() = runTest {
        enqueueTheme()
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)

        repo.fetchPageTheme("home")
        repo.fetchPageTheme("store")

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("theme_page_home"))
        assertTrue(cache.store.containsKey("theme_page_store"))
    }
    // endregion

    // region Force refresh
    @Test
    fun `forceRefresh bypasses a fresh page theme cache entry`() = runTest {
        enqueueTheme()
        enqueueTheme(pageColor = "#00FF00")
        val repo = ThemeRepositoryImpl(apiService, cache)

        val cached = repo.fetchPageTheme("home")
        val refreshed = repo.fetchPageTheme("home", forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("#FF0000", cached?.color)
        assertEquals("#00FF00", refreshed?.color)
    }

    @Test
    fun `forceRefresh bypasses a fresh destination theme cache entry`() = runTest {
        enqueueTheme()
        enqueueTheme(destinationImage = "https://cdn/za-2.png")
        val repo = ThemeRepositoryImpl(apiService, cache)

        val cached = repo.fetchDestinationTheme("za")
        val refreshed = repo.fetchDestinationTheme("za", forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("https://cdn/za.png", cached?.image?.url)
        assertEquals("https://cdn/za-2.png", refreshed?.image?.url)
    }
    // endregion

    // region Stale fallback
    @Test
    fun `a failed page theme fetch falls back to expired data and reports it as stale`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)
        repo.fetchPageTheme("home", cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchPageThemeResult("home")

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("#FF0000", result.value?.color)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
    }

    @Test
    fun `the plain page theme read serves expired data instead of throwing`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)
        repo.fetchPageTheme("home", cacheTTL = Duration.ZERO)

        enqueueServerError()

        assertEquals("#FF0000", repo.fetchPageTheme("home")?.color)
    }

    @Test
    fun `a failed destination theme fetch falls back to expired data`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)
        repo.fetchDestinationTheme("za", cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchDestinationThemeResult("za")

        assertEquals("https://cdn/za.png", result.value?.image?.url)
        assertTrue(result.isStale)
    }

    @Test
    fun `a failed fetch with no cached theme throws from the plain read`() = runTest {
        enqueueServerError()
        val repo = ThemeRepositoryImpl(apiService, cache)

        var thrown: Throwable? = null
        try {
            repo.fetchPageTheme("home")
        } catch (error: Throwable) {
            thrown = error
        }

        assertNotNull(thrown)
    }

    @Test
    fun `a failed fetch with no cached theme reports failure without staleness`() = runTest {
        enqueueServerError()
        val repo = ThemeRepositoryImpl(apiService, cache)

        val result = repo.fetchPageThemeResult("home")

        assertNull(result.value)
        assertFalse(result.isStale)
        assertTrue(result.didFail)
    }
    // endregion

    // region Missing entries
    @Test
    fun `a page absent from the response returns null without caching or failing`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)

        val result = repo.fetchPageThemeResult("missing")

        assertNull(result.value)
        assertFalse(result.didFail)
        assertFalse(cache.store.containsKey("theme_page_missing"))
    }

    @Test
    fun `a destination absent from the response returns null without failing`() = runTest {
        enqueueTheme()
        val repo = ThemeRepositoryImpl(apiService, cache)

        val result = repo.fetchDestinationThemeResult("gb")

        assertNull(result.value)
        assertFalse(result.didFail)
    }
    // endregion

    // region Invalidation
    @Test
    fun `Theme invalidateCache clears every theme key but leaves others`() = runTest {
        cache.set("theme_page_home", ThemePage(color = "#FF0000"))
        cache.set("theme_destination_za", ThemePage(color = "#00FF00"))
        cache.set("countries_all", listOf("ZA"))

        ThemeRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<ThemePage>("theme_page_home"))
        assertNull(cache.getExpired<ThemePage>("theme_destination_za"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
        assertEquals(0, mockWebServer.requestCount)
    }
    // endregion

    // region Ttl
    @Test
    fun `the default theme ttl is one hour`() {
        assertEquals(3600.seconds, ThemeRepository.THEME_TTL)
    }

    @Test
    fun `an explicit cacheTTL overrides the cache default`() = runTest {
        enqueueTheme()

        ThemeRepositoryImpl(apiService, cache).fetchPageTheme("home", cacheTTL = 5.seconds)

        val entry = cache.store["theme_page_home"]
        assertNotNull(entry)
        assertTrue(entry!!.expiresAt - System.currentTimeMillis() <= 5_000)
    }
    // endregion

    private fun enqueueTheme(
        pageColor: String = "#FF0000",
        destinationImage: String = "https://cdn/za.png",
    ) {
        enqueueJson(
            """
            {
              "version": "1",
              "cdnBase": "https://cdn",
              "pages": {
                "home": {
                  "urlPath": "/home",
                  "featuredImage": { "url": "https://cdn/home.png", "accent": "#123456" },
                  "color": "$pageColor"
                },
                "store": { "urlPath": "/store", "color": "#0000FF" }
              },
              "destinations": {
                "south-africa": {
                  "image": { "url": "$destinationImage" },
                  "gallery": ["https://cdn/za-1.png"],
                  "countryCode": "za"
                }
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
