package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.OrderDetail
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

    // region Unpaged reads
    @Test
    fun `the unpaged request sends the unpaged limit`() = runTest {
        enqueueOrders()

        repo().getOrderHistoryResult()

        val path = mockWebServer.takeRequest().path
        assertNotNull(path)
        assertTrue(path!!.contains("limit=500"))
        assertFalse(path.contains("offset"))
        assertFalse(path.contains("used_points"))
    }

    @Test
    fun `the unpaged request with loyalty points sends the unpaged limit alongside used_points`() = runTest {
        enqueueOrders()

        repo().getOrderHistoryResult(withLoyaltyPoints = true)

        val path = mockWebServer.takeRequest().path
        assertNotNull(path)
        assertTrue(path!!.contains("limit=500"))
        assertTrue(path.contains("used_points=true"))
        assertFalse(path.contains("offset"))
    }
    // endregion

    // region Pending order retries
    @Test
    fun `a ready order returns on the first attempt`() = runTest {
        enqueueOrderDetail(status = "completed")

        val order = repo().getOrderDetails("uuid-1")

        assertEquals("completed", order.orderStatus)
        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `a ready order is cached and a repeat call skips the network`() = runTest {
        enqueueOrderDetail(status = "completed")
        val repo = repo()

        repo.getOrderDetails("uuid-1")
        val second = repo.getOrderDetails("uuid-1")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals("completed", second.orderStatus)
    }

    @Test
    fun `a pending order is retried until it turns ready`() = runTest {
        enqueueOrderDetail(status = "pending")
        enqueueOrderDetail(status = "pending")
        enqueueOrderDetail(status = "completed")

        val order = repo().getOrderDetails("uuid-1")

        assertEquals("completed", order.orderStatus)
        assertEquals(3, mockWebServer.requestCount)
    }

    @Test
    fun `a pending order is attempted five times and the pending order is still returned`() = runTest {
        repeat(6) { enqueueOrderDetail(status = "pending") }

        val result = repo().getOrderDetailsResult("uuid-1")

        assertEquals(5, mockWebServer.requestCount)
        assertNotNull(result.value)
        assertEquals("pending", result.value!!.orderStatus)
        assertFalse(result.didFail)
    }

    @Test
    fun `an exhausted pending order is not cached and a later call refetches`() = runTest {
        repeat(5) { enqueueOrderDetail(status = "pending") }
        val repo = repo()

        repo.getOrderDetailsResult("uuid-1")

        assertFalse(cache.store.containsKey("order_uuid-1"))
        assertNull(cache.getExpired<OrderDetail>("order_uuid-1"))

        enqueueOrderDetail(status = "completed")
        val second = repo.getOrderDetails("uuid-1")

        assertEquals(6, mockWebServer.requestCount)
        assertEquals("completed", second.orderStatus)
    }

    @Test
    fun `a failed order detail fetch is not retried`() = runTest {
        enqueueServerError()

        val result = repo().getOrderDetailsResult("uuid-1")

        assertEquals(1, mockWebServer.requestCount)
        assertNull(result.value)
        assertTrue(result.didFail)
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

    // region Undecodable rows
    @Test
    fun `a page with one undecodable row surfaces a failure rather than a bare empty page`() = runTest {
        enqueueJson(
            """{"count":2,"next":null,"previous":null,"results":[$UNDECODABLE_ORDER_JSON,$ORDER_JSON]}"""
        )

        val result = repo().getOrdersPageResult(limit = 10, offset = 0)

        assertTrue(result.value.orders.isEmpty())
        assertTrue(result.didFail)
        assertFalse(result.isOffline)
        assertFalse(result.isStale)
    }

    @Test
    fun `an unpaged history with one undecodable row surfaces a failure`() = runTest {
        enqueueJson(
            """{"count":2,"next":null,"previous":null,"results":[$UNDECODABLE_ORDER_JSON,$ORDER_JSON]}"""
        )

        val result = repo().getOrderHistoryResult()

        assertTrue(result.value.isEmpty())
        assertTrue(result.didFail)
    }

    @Test
    fun `an unpaged history with one undecodable row throws from the non-result call`() = runTest {
        enqueueJson(
            """{"count":2,"next":null,"previous":null,"results":[$UNDECODABLE_ORDER_JSON,$ORDER_JSON]}"""
        )

        val thrown = runCatching { repo().getOrderHistory() }.exceptionOrNull()

        assertNotNull(thrown)
    }

    @Test
    fun `a page of rows with null countries decodes every row`() = runTest {
        val row = ORDER_JSON.replace(COUNTRY_OBJECT_JSON, "null")
        val rows = List(3) { row }.joinToString(",")
        enqueueJson("""{"count":3,"next":null,"previous":null,"results":[$rows]}""")

        val result = repo().getOrdersPageResult(limit = 10, offset = 0)

        assertFalse(result.didFail)
        assertEquals(3, result.value.orders.size)
        assertNull(result.value.orders.first().country)
    }

    @Test
    fun `an order detail with a null country decodes`() = runTest {
        enqueueJson(ORDER_DETAIL_JSON.replace("ORDER_STATUS_PLACEHOLDER", "COMPLETE").replace(
            COUNTRY_OBJECT_JSON,
            "null",
        ))

        val result = repo().getOrderDetailsResult("uuid-1")

        assertFalse(result.didFail)
        assertNotNull(result.value)
        assertNull(result.value?.country)
        assertEquals(1001, result.value?.orderNumber)
    }
    // endregion

    private fun repo() = OrdersRepositoryImpl(apiService, cache)

    private fun enqueueOrders(count: Int = 1, next: String? = null) {
        val nextField = if (next == null) "null" else "\"$next\""
        enqueueJson("""{"count":$count,"next":$nextField,"previous":null,"results":[$ORDER_JSON]}""")
    }

    private fun enqueueOrderDetail(status: String) {
        enqueueJson(ORDER_DETAIL_JSON.replace("ORDER_STATUS_PLACEHOLDER", status))
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
        const val ORDER_DETAIL_JSON = """
            {
                "customer_id": "cust-1",
                "discount_amount": 0.0,
                "discount_code": "",
                "final_price": 9.99,
                "order_date": "2026-01-01",
                "order_number": 1001,
                "order_status": "ORDER_STATUS_PLACEHOLDER",
                "order_type": "BUY",
                "package_data_size": 1.0,
                "package_type_id": 42,
                "package_name": "1 GB / 7 Days",
                "package_validity": 7,
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "purchase_price": 9.99,
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

        const val COUNTRY_OBJECT_JSON = """{
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                }"""

        const val UNDECODABLE_ORDER_JSON = """
            {
                "esim": {
                    "assigned_date": "2026-01-01",
                    "iccid": "8911",
                    "matching_id": "MATCH",
                    "premium": false,
                    "sm_dp_address": "sm-dp.example.com"
                },
                "order_number": null,
                "order_uuid": "uuid-2",
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
                "payment_method": "stripe_intent"
            }
        """

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
