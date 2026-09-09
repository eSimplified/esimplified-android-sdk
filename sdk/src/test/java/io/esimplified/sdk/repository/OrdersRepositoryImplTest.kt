package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.OrdersRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
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

class OrdersRepositoryImplTest {

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

    // region Paging
    @Test
    fun `a page carries the envelope count and reports more when next is present`() = runTest {
        enqueueOrders(count = 42, next = "https://api/customer/orders/?limit=2&offset=2")

        val page = repo().getOrdersPageResult(limit = 2, offset = 0).value

        assertEquals(42, page.totalCount)
        assertTrue(page.hasMore)
        assertEquals(1, page.orders.size)
        assertEquals("uuid-1", page.orders.first().orderUUID)
    }

    @Test
    fun `a page reports no more when next is null`() = runTest {
        enqueueOrders(count = 1, next = null)

        val page = repo().getOrdersPageResult(limit = 100, offset = 0).value

        assertEquals(1, page.totalCount)
        assertFalse(page.hasMore)
    }

    @Test
    fun `a page reports no more when next is an empty string`() = runTest {
        enqueueOrders(count = 1, next = "")

        assertFalse(repo().getOrdersPageResult().value.hasMore)
    }

    @Test
    fun `the page request sends limit and offset`() = runTest {
        enqueueOrders()

        repo().getOrdersPageResult(limit = 25, offset = 50)

        val path = mockWebServer.takeRequest().path
        assertNotNull(path)
        assertTrue(path!!.contains("limit=25"))
        assertTrue(path.contains("offset=50"))
        assertFalse(path.contains("used_points"))
    }

    @Test
    fun `the page request sends used_points only with loyalty points`() = runTest {
        enqueueOrders()

        repo().getOrdersPageResult(withLoyaltyPoints = true)

        assertTrue(mockWebServer.takeRequest().path!!.contains("used_points=true"))
    }
    // endregion

    // region Page caching
    @Test
    fun `a fresh page is served without touching the network`() = runTest {
        enqueueOrders(count = 7)
        val repo = repo()

        val first = repo.getOrdersPageResult(limit = 10, offset = 0).value
        val second = repo.getOrdersPageResult(limit = 10, offset = 0).value

        assertEquals(1, mockWebServer.requestCount)
        assertEquals(first, second)
        assertEquals(7, second.totalCount)
    }

    @Test
    fun `each offset caches separately`() = runTest {
        enqueueOrders()
        enqueueOrders()
        val repo = repo()

        repo.getOrdersPageResult(limit = 10, offset = 0)
        repo.getOrdersPageResult(limit = 10, offset = 10)

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("orders_page_false_10_0"))
        assertTrue(cache.store.containsKey("orders_page_false_10_10"))
    }

    @Test
    fun `forceRefresh bypasses a fresh page`() = runTest {
        enqueueOrders(count = 1)
        enqueueOrders(count = 2)
        val repo = repo()

        val cached = repo.getOrdersPageResult(limit = 10, offset = 0)
        val refreshed = repo.getOrdersPageResult(limit = 10, offset = 0, forceRefresh = true)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals(1, cached.value.totalCount)
        assertEquals(2, refreshed.value.totalCount)
    }

    @Test
    fun `a failed page fetch falls back to expired data and reports it as stale`() = runTest {
        enqueueOrders(count = 5)
        val repo = repo()
        repo.getOrdersPageResult(limit = 10, offset = 0, cacheTTL = Duration.ZERO)

        enqueueServerError()
        val result = repo.getOrdersPageResult(limit = 10, offset = 0)

        assertEquals(5, result.value.totalCount)
        assertTrue(result.isStale)
        assertTrue(result.didFail)
    }

    @Test
    fun `a failed page fetch with nothing cached yields an empty page`() = runTest {
        enqueueServerError()

        val result = repo().getOrdersPageResult(limit = 10, offset = 0)

        assertTrue(result.value.orders.isEmpty())
        assertEquals(0, result.value.totalCount)
        assertFalse(result.value.hasMore)
        assertFalse(result.isStale)
        assertTrue(result.didFail)
    }

    @Test
    fun `invalidateCache clears page entries alongside the unpaged lists`() = runTest {
        cache.set("orders_page_false_10_0", listOf("page"))
        cache.set("orders_none", listOf("a"))
        cache.set("order_uuid-1", "detail")
        cache.set("countries_all", listOf("ZA"))

        repo().invalidateCache()

        assertNull(cache.getExpired<List<String>>("orders_page_false_10_0"))
        assertNull(cache.getExpired<List<String>>("orders_none"))
        assertNull(cache.getExpired<String>("order_uuid-1"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
    }
    // endregion

    // region Invoice
    @Test
    fun `an invoice comes back as raw bytes`() = runTest {
        val pdf = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A, 0x00, 0x01)
        enqueueBytes(pdf)

        val bytes = repo().getOrderInvoice("uuid-1")

        assertArrayEquals(pdf, bytes)
    }

    @Test
    fun `an invoice is requested from the order invoice endpoint`() = runTest {
        enqueueBytes(byteArrayOf(0x25))

        repo().getOrderInvoice("uuid-1")

        val request = mockWebServer.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/api/v2/orders/uuid-1/invoice/", request.path)
    }

    @Test
    fun `an invoice is never cached`() = runTest {
        enqueueBytes(byteArrayOf(0x25))

        repo().getOrderInvoice("uuid-1")

        assertTrue(cache.store.isEmpty())
    }

    @Test
    fun `a failed invoice request throws`() = runTest {
        enqueueServerError()

        var thrown: Throwable? = null
        try {
            repo().getOrderInvoice("uuid-1")
        } catch (error: Throwable) {
            thrown = error
        }

        assertNotNull(thrown)
    }
    // endregion

    private fun repo() = OrdersRepositoryImpl(apiService, cache)

    private fun enqueueOrders(count: Int = 1, next: String? = null) {
        val nextField = if (next == null) "null" else "\"$next\""
        enqueueJson("""{"count":$count,"next":$nextField,"previous":null,"results":[$ORDER_JSON]}""")
    }

    private fun enqueueServerError() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("""{"detail":"boom"}"""))
    }

    private fun enqueueBytes(bytes: ByteArray) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setBody(Buffer().write(bytes))
        )
    }

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private companion object {
        const val ORDER_JSON = """
            {
                "esim": {
                    "assigned_date": "2026-01-01",
                    "iccid": "8910",
                    "matching_id": "MATCH",
                    "premium": false,
                    "sm_dp_address": "sm-dp.example.com"
                },
                "order_number": 1001,
                "order_uuid": "uuid-1",
                "order_type": "BUY",
                "package_id": "pkg-1",
                "final_price": "9.99",
                "package_name": "1 GB / 7 Days",
                "purchase_date": "2026-01-01",
                "purchase_price": "9.99",
                "discount_code": "",
                "discount_amount": "0.00",
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "package_type_id": 42,
                "payment_status": "paid",
                "payment_method": "stripe_intent",
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                }
            }
        """
    }
}
