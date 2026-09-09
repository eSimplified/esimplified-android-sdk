package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.AssignedEsim
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackageDetail
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.OrdersRepositoryImpl
import io.esimplified.sdk.repository.impl.PackagesRepositoryImpl
import io.esimplified.sdk.repository.impl.VisaRewardsRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class ParityGapTest {

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

    // region Order history survives an order with no country
    @Test
    fun `an order whose country is null still reaches the caller`() = runTest {
        enqueueJson(ordersEnvelope(ORDER_WITH_NULL_COUNTRY))

        val orders = OrdersRepositoryImpl(apiService, cache).getOrderHistory()

        assertEquals(1, orders.size)
        assertNull(orders.first().country)
        assertEquals("uuid-1", orders.first().orderUUID)
    }

    @Test
    fun `an order with no country key at all still reaches the caller`() = runTest {
        enqueueJson(ordersEnvelope(ORDER_WITHOUT_COUNTRY))

        val orders = OrdersRepositoryImpl(apiService, cache).getOrderHistory()

        assertEquals(1, orders.size)
        assertNull(orders.first().country)
    }

    @Test
    fun `one countryless order does not empty the whole page`() = runTest {
        enqueueJson(ordersEnvelope(ORDER_WITH_NULL_COUNTRY, ORDER_WITH_COUNTRY))

        val page = OrdersRepositoryImpl(apiService, cache).getOrdersPageResult(limit = 20, offset = 0)

        assertEquals(2, page.value.orders.size)
        assertEquals(2, page.value.totalCount)
        assertNull(page.failure)
    }

    @Test
    fun `an order that does carry a country still decodes it`() = runTest {
        enqueueJson(ordersEnvelope(ORDER_WITH_COUNTRY))

        val orders = OrdersRepositoryImpl(apiService, cache).getOrderHistory()

        assertEquals("Australia", orders.first().country?.name)
    }
    // endregion

    // region The Visa iframe carries the vendor
    @Test
    fun `the EU iframe read sends the vendor body`() = runTest {
        enqueueJson("""{"token":"t","iframe_url":"https://example.com"}""")

        VisaRewardsRepositoryImpl(apiService).getIframe(isEU = true)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("""{"vendor":"eu"}""", request.body.readUtf8())
    }

    @Test
    fun `the non-EU iframe read sends no body at all`() = runTest {
        enqueueJson("""{"token":"t","iframe_url":"https://example.com"}""")

        VisaRewardsRepositoryImpl(apiService).getIframe(isEU = false)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("", request.body.readUtf8())
    }
    // endregion

    // region A response-level promo code is reachable
    @Test
    fun `the packages page carries the response-level promo code`() = runTest {
        enqueueJson(PACKAGES_WITH_PROMO)

        val page = PackagesRepositoryImpl(apiService, cache)
            .getPackagesPage(Destination(code = "AU", name = null, slug = null))

        assertEquals("SUMMER10", page.promoCode?.discount)
        assertEquals(10.0, page.promoCode?.percentage!!, 0.0)
        assertEquals(1, page.totalCount)
        assertEquals(1, page.packages.size)
    }

    @Test
    fun `the packages page reports no promo code when the response carries none`() = runTest {
        enqueueJson(PACKAGES_WITHOUT_PROMO)

        val page = PackagesRepositoryImpl(apiService, cache)
            .getPackagesPage(Destination(code = "AU", name = null, slug = null))

        assertNull(page.promoCode)
    }

    @Test
    fun `the packages page keeps its own cache entry from the plain list read`() = runTest {
        enqueueJson(PACKAGES_WITH_PROMO)
        enqueueJson(PACKAGES_WITH_PROMO)
        val repo = PackagesRepositoryImpl(apiService, cache)
        val destination = Destination(code = "AU", name = null, slug = null)

        repo.getPackages(destination)
        repo.getPackagesPage(destination)

        assertEquals(2, mockWebServer.requestCount)
    }
    // endregion

    // region Visa reward counts tell absent from zero
    @Test
    fun `a reward response without the counts reports them as absent`() {
        val response = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            .decodeFromString<VisaRewardsResponse>("""{"eligible":true,"allowed_count":3}""")

        assertNull(response.remaining)
        assertNull(response.used)
        assertEquals(3, response.allowed)
        assertEquals(3, response.remainingOrAllowed)
    }

    @Test
    fun `a reward response with no rewards left reports zero, not absent`() {
        val response = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            .decodeFromString<VisaRewardsResponse>(
                """{"eligible":true,"allowed_count":3,"remaining_count":0,"used_count":3}"""
            )

        assertEquals(0, response.remaining)
        assertEquals(3, response.used)
        assertEquals(0, response.remainingOrAllowed)
    }
    // endregion

    // region hasUnlimitedPackage
    @Test
    fun `an eSIM with minus one gigabytes remaining is unlimited`() {
        assertTrue(esim(remainingGigabytes = -1.0).hasUnlimitedPackage)
        assertFalse(esim(remainingGigabytes = 0.0).hasUnlimitedPackage)
        assertFalse(esim(remainingGigabytes = 5.0).hasUnlimitedPackage)
    }

    @Test
    fun `a package detail is unlimited when either gigabyte figure is minus one`() {
        assertTrue(packageDetail(allowance = -1.0, remaining = 0.0).hasUnlimitedPackage)
        assertTrue(packageDetail(allowance = 5.0, remaining = -1.0).hasUnlimitedPackage)
        assertFalse(packageDetail(allowance = 5.0, remaining = 2.0).hasUnlimitedPackage)
    }
    // endregion

    private fun esim(remainingGigabytes: Double) = AssignedEsim(
        iccid = "1",
        name = null,
        assignedDate = "",
        isArchived = false,
        isAutoTopUp = false,
        dataUsageRemainingGigabytes = remainingGigabytes,
    )

    private fun packageDetail(allowance: Double, remaining: Double) = PackageDetail(
        status = "ACTIVE",
        dateCreatedEpoch = 0,
        packageCountryName = "Australia",
        dataAllowanceGigabytes = allowance,
        dataUsageRemainingGigabytes = remaining,
    )

    private fun ordersEnvelope(vararg orders: String) =
        """{"count":${orders.size},"next":null,"previous":null,"results":[${orders.joinToString(",")}]}"""

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private companion object {
        const val ORDER_BODY = """
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
            "payment_method": "stripe_intent"
        """

        const val ORDER_WITH_NULL_COUNTRY = """{$ORDER_BODY, "country": null}"""

        const val ORDER_WITHOUT_COUNTRY = """{$ORDER_BODY}"""

        const val ORDER_WITH_COUNTRY = """
            {$ORDER_BODY, "country": {
                "country_name": "Australia",
                "country_code": "AU",
                "country_flag": "flag.png",
                "country_flag_css": "au",
                "country_name_slug": "australia"
            }}
        """

        const val PACKAGE_BODY = """
            {
                "name": "1 GB / 7 Days",
                "price": 9.99,
                "data_GB": 1.0,
                "currency": "USD",
                "currency_obj": {"symbol": "$", "iso": "USD"},
                "plan_type": "DATA",
                "kyc_display": "",
                "package_slug": "au-1gb-7d",
                "validity_days": 7,
                "package_type_id": 42,
                "best_connectivity": "",
                "activation_policy": "",
                "name_additional_text": "",
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                }
            }
        """

        const val PACKAGES_WITH_PROMO = """
            {"count":1,"next":null,"previous":null,
             "promo_code":{"valid":true,"discount_code":"SUMMER10","discount_percentage":10.0},
             "results":[$PACKAGE_BODY]}
        """

        const val PACKAGES_WITHOUT_PROMO = """
            {"count":1,"next":null,"previous":null,"results":[$PACKAGE_BODY]}
        """
    }
}
