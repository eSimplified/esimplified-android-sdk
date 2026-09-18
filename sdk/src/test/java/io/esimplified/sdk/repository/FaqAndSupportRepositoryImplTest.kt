package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.ContentBlock
import io.esimplified.sdk.model.ContentDocument
import io.esimplified.sdk.model.ContentListMarker
import io.esimplified.sdk.model.Faq
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.FaqAndSupportRepositoryImpl
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

class FaqAndSupportRepositoryImplTest {

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
    fun `destination FAQs are requested with the country slug in the path`() = runTest {
        enqueueFaqs()

        FaqAndSupportRepositoryImpl(apiService, cache).fetchDestinationFaqs("south-africa")

        assertEquals("/api/v2/faqs/destinations/south-africa/", mockWebServer.takeRequest().path)
    }
    // endregion

    // region Fresh cache hits
    @Test
    fun `a fresh cached entry is served without touching the network`() = runTest {
        enqueueFaqs()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val first = repo.fetchDestinationFaqs("south-africa")
        val second = repo.fetchDestinationFaqs("south-africa")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals(2, second.size)
        assertEquals("Does it work?", second.first().question)
        assertEquals("Yes.", second.first().answer)
    }

    @Test
    fun `a fresh cached entry reports itself as neither stale nor failed`() = runTest {
        enqueueFaqs()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)
        repo.fetchDestinationFaqs("south-africa")

        val result = repo.fetchDestinationFaqsResult("south-africa")

        assertEquals(1, mockWebServer.requestCount)
        assertFalse(result.isStale)
        assertFalse(result.didFail)
        assertNull(result.failure)
    }

    @Test
    fun `each destination slug caches separately`() = runTest {
        enqueueFaqs()
        enqueueFaqs(slug = "australia")
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        repo.fetchDestinationFaqs("south-africa")
        repo.fetchDestinationFaqs("australia")

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("faqs_destination_south-africa"))
        assertTrue(cache.store.containsKey("faqs_destination_australia"))
    }
    // endregion

    // region Force refresh
    @Test
    fun `forceRefresh bypasses a fresh cache entry`() = runTest {
        enqueueFaqs()
        enqueueFaqs(firstQuestion = "Is it fast?")
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val cached = repo.fetchDestinationFaqs("south-africa")
        val refreshed = repo.fetchDestinationFaqs("south-africa", forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Does it work?", cached.first().question)
        assertEquals("Is it fast?", refreshed.first().question)
    }

    @Test
    fun `forceRefresh writes the new value back into the cache`() = runTest {
        enqueueFaqs()
        enqueueFaqs(firstQuestion = "Is it fast?")
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)
        repo.fetchDestinationFaqs("south-africa")

        repo.fetchDestinationFaqs("south-africa", forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Is it fast?", repo.fetchDestinationFaqs("south-africa").first().question)
    }
    // endregion

    // region Stale fallback
    @Test
    fun `a failed fetch falls back to expired data and reports it as stale`() = runTest {
        enqueueFaqs()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)
        repo.fetchDestinationFaqs("south-africa", cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchDestinationFaqsResult("south-africa")

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Does it work?", result.value.first().question)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `a failed fetch with no cached data returns an empty list instead of throwing`() = runTest {
        enqueueServerError()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val faqs = repo.fetchDestinationFaqs("south-africa")

        assertTrue(faqs.isEmpty())
    }

    @Test
    fun `a failed fetch with no cached data reports failure without staleness`() = runTest {
        enqueueServerError()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val result = repo.fetchDestinationFaqsResult("south-africa")

        assertTrue(result.value.isEmpty())
        assertFalse(result.isStale)
        assertTrue(result.didFail)
    }
    // endregion

    // region Content documents
    @Test
    fun `terms are requested from the content endpoint with no query string`() = runTest {
        enqueueDocument()

        FaqAndSupportRepositoryImpl(apiService, cache).fetchTerms("en")

        assertEquals("/api/v2/terms/", mockWebServer.takeRequest().path)
    }

    @Test
    fun `privacy is requested from the content endpoint with no query string`() = runTest {
        enqueueDocument()

        FaqAndSupportRepositoryImpl(apiService, cache).fetchPrivacy("ar")

        assertEquals("/api/v2/privacy/", mockWebServer.takeRequest().path)
    }

    @Test
    fun `general FAQs are requested from the content endpoint with no query string`() = runTest {
        enqueueDocument()

        FaqAndSupportRepositoryImpl(apiService, cache).fetchFaqs("en")

        assertEquals("/api/v2/faqs/", mockWebServer.takeRequest().path)
    }

    @Test
    fun `a content document decodes its sections and blocks`() = runTest {
        enqueueDocument()

        val document = FaqAndSupportRepositoryImpl(apiService, cache).fetchTerms("en")

        assertNotNull(document)
        assertEquals("en", document!!.language)
        assertEquals("Last updated: 28 April 2025", document.updatedAt)
        assertEquals(1, document.children.size)
        assertEquals(
            listOf(ContentBlock.Paragraph("Body.")),
            document.children.first().blocks,
        )
    }

    @Test
    fun `a fresh cached document is served without touching the network`() = runTest {
        enqueueDocument()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val first = repo.fetchFaqs("en")
        val second = repo.fetchFaqsResult("en")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second.value)
        assertFalse(second.isStale)
        assertFalse(second.didFail)
        assertNotNull(cache.getExpired<ContentDocument>("faqs_en"))
    }

    @Test
    fun `each language caches its document separately`() = runTest {
        enqueueDocument()
        enqueueDocument(language = "ar")
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        repo.fetchPrivacy("en")
        repo.fetchPrivacy("ar")

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("privacy_en"))
        assertTrue(cache.store.containsKey("privacy_ar"))
    }

    @Test
    fun `forceRefresh bypasses a fresh document cache entry`() = runTest {
        enqueueDocument()
        enqueueDocument(title = "Newer")
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val cached = repo.fetchTerms("en")
        val refreshed = repo.fetchTerms("en", forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Doc", cached?.title)
        assertEquals("Newer", refreshed?.title)
    }

    @Test
    fun `a failed document fetch falls back to the expired entry and reports it as stale`() = runTest {
        enqueueDocument()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)
        repo.fetchTerms("en", cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.fetchTermsResult("en")

        assertEquals(2, mockWebServer.requestCount)
        assertEquals("Doc", result.value?.title)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `a failed document fetch with no cached data returns null and the failure`() = runTest {
        enqueueServerError()
        val repo = FaqAndSupportRepositoryImpl(apiService, cache)

        val result = repo.fetchPrivacyResult("en")

        assertNull(result.value)
        assertFalse(result.isStale)
        assertTrue(result.didFail)
        assertNotNull(result.failure)
    }

    @Test
    fun `a failed document fetch with no cached data returns null from the plain method`() = runTest {
        enqueueServerError()

        val document = FaqAndSupportRepositoryImpl(apiService, cache).fetchFaqs("en")

        assertNull(document)
    }

    @Test
    fun `an unknown block type in a live document does not fail the whole read`() = runTest {
        enqueueJson(
            """
            {"language":"en","id":"doc","title":"Doc","blocks":[
              {"type":"carousel","slides":[]},
              {"type":"list","ordered":true,"marker":"roman","items":[{"text":"One"}]}
            ],"children":[]}
            """.trimIndent()
        )

        val document = FaqAndSupportRepositoryImpl(apiService, cache).fetchTerms("en")

        assertNotNull(document)
        assertEquals(ContentBlock.Unknown, document!!.blocks.first())
        val list = (document.blocks[1] as ContentBlock.ListBlock).list
        assertEquals(ContentListMarker.BULLET, list.marker)
        assertEquals("One", list.items.single().text)
    }

    @Test
    fun `the document cacheTTL argument overrides the cache default`() = runTest {
        enqueueDocument()

        FaqAndSupportRepositoryImpl(apiService, cache).fetchTerms("en", cacheTTL = 5.seconds)

        val entry = cache.store["terms_en"]
        assertNotNull(entry)
        assertTrue(entry!!.expiresAt - System.currentTimeMillis() <= 5_000)
    }
    // endregion

    // region Invalidation
    @Test
    fun `Faq invalidateCache clears every faqs key but leaves others`() = runTest {
        cache.set("faqs_destination_south-africa", listOf(Faq("q", "a")))
        cache.set("faqs_destination_australia", listOf(Faq("q", "a")))
        cache.set("countries_all", listOf("ZA"))

        FaqAndSupportRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<List<Faq>>("faqs_destination_south-africa"))
        assertNull(cache.getExpired<List<Faq>>("faqs_destination_australia"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun `Faq invalidateCache clears the faqs terms and privacy prefixes`() = runTest {
        val document = ContentDocument(language = "en")
        cache.set("faqs_en", document)
        cache.set("faqs_destination_south-africa", listOf(Faq("q", "a")))
        cache.set("terms_en", document)
        cache.set("privacy_en", document)
        cache.set("theme_page_home", "untouched")

        FaqAndSupportRepositoryImpl(apiService, cache).invalidateCache()

        assertNull(cache.getExpired<ContentDocument>("faqs_en"))
        assertNull(cache.getExpired<List<Faq>>("faqs_destination_south-africa"))
        assertNull(cache.getExpired<ContentDocument>("terms_en"))
        assertNull(cache.getExpired<ContentDocument>("privacy_en"))
        assertEquals("untouched", cache.getExpired<String>("theme_page_home"))
    }
    // endregion

    // region Ttl
    @Test
    fun `the default faqs ttl is one day`() {
        assertEquals(86400.seconds, FaqAndSupportRepository.FAQS_TTL)
    }

    @Test
    fun `an explicit cacheTTL overrides the cache default`() = runTest {
        enqueueFaqs()

        FaqAndSupportRepositoryImpl(apiService, cache)
            .fetchDestinationFaqs("south-africa", cacheTTL = 5.seconds)

        val entry = cache.store["faqs_destination_south-africa"]
        assertNotNull(entry)
        assertTrue(entry!!.expiresAt - System.currentTimeMillis() <= 5_000)
    }
    // endregion

    private fun enqueueFaqs(
        slug: String = "south-africa",
        firstQuestion: String = "Does it work?",
    ) {
        enqueueJson(
            """
            {
              "slug": "$slug",
              "name": "South Africa",
              "language": "en",
              "faqs": [
                { "question": "$firstQuestion", "answer": "Yes." },
                { "question": "How much?", "answer": "Ten." }
              ]
            }
            """.trimIndent()
        )
    }

    private fun enqueueDocument(
        language: String = "en",
        title: String = "Doc",
    ) {
        enqueueJson(
            """
            {
              "language": "$language",
              "id": "doc",
              "title": "$title",
              "description": null,
              "updatedAt": "Last updated: 28 April 2025",
              "blocks": [],
              "children": [
                {
                  "id": "one",
                  "title": "One",
                  "description": null,
                  "updatedAt": null,
                  "blocks": [{ "type": "paragraph", "text": "Body." }],
                  "children": []
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
