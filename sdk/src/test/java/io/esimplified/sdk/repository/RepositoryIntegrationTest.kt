package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.SdkConfig
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkAuthInterceptor
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.AuthRepositoryImpl
import io.esimplified.sdk.repository.impl.CountryRepositoryImpl
import io.esimplified.sdk.repository.impl.LoyaltyRepositoryImpl
import io.esimplified.sdk.repository.impl.NotificationRepositoryImpl
import io.esimplified.sdk.repository.impl.OrdersRepositoryImpl
import io.esimplified.sdk.repository.impl.PackagesRepositoryImpl
import io.esimplified.sdk.repository.impl.PaymentsRepositoryImpl
import io.esimplified.sdk.repository.impl.PromoCodeRepositoryImpl
import io.esimplified.sdk.repository.impl.UserRepositoryImpl
import io.esimplified.sdk.repository.impl.VisaRewardsRepositoryImpl
import io.esimplified.sdk.repository.impl.VouchersRepositoryImpl
import io.esimplified.sdk.repository.impl.EsimRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.boolean
import io.esimplified.sdk.model.SavedPaymentMethod
import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.CustomerDetails
import io.esimplified.sdk.network.SdkError
import org.junit.Assert.assertNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create
import java.time.LocalDateTime

/**
 * Integration tests that exercise each repository through the full Retrofit + OkHttp + interceptor pipeline,
 * using a MockWebServer to simulate API responses.
 */
class RepositoryIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var sessionManager: DefaultSessionManager
    private lateinit var storage: FakeSecureStorage

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        storage = FakeSecureStorage()
        sessionManager = DefaultSessionManager(storage)
        sessionManager.save(
            Auth.Authenticated(
                user = Customer(
                    id = "u-1",
                    email = "u@e.com",
                    preferredCurrency = null,
                    preferredLanguage = null,
                ),
                accessToken = "tok",
                refreshToken = "ref",
                expires = LocalDateTime.now().plusHours(1)
            )
        )

        val config = SdkConfig.forTesting(
            baseUrl = mockWebServer.url("/").toString().trimEnd('/'),
            clientId = "id",
            clientSecret = "secret"
        )
        val interceptor = SdkAuthInterceptor(sessionManager, config)
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun enqueueJson(json: String, code: Int = 200) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(json)
        )
    }

    // MARK: - AuthRepository

    @Test
    fun `AuthRepository login persists session on success`() = runTest {
        sessionManager.save(Auth.Unauthenticated)
        val payload = """
            {
                "access_token": "new-tok",
                "refresh_token": "new-ref",
                "expires_in": 3600,
                "token_type": "Bearer",
                "user": {"customer_id": "u-2", "email": "u2@e.com"}
            }
        """.trimIndent()
        enqueueJson(payload)

        val repo = AuthRepositoryImpl(apiService, sessionManager, storage, SdkCache())
        val customer = repo.login("u2@e.com", "p")

        assertEquals("u-2", customer.id)
        assertEquals("new-tok", sessionManager.getAccessToken())
        assertTrue(sessionManager.isAuthenticated())

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/auth/token"))
    }

    @Test
    fun `AuthRepository logout clears session`() = runTest {
        val repo = AuthRepositoryImpl(apiService, sessionManager, storage, SdkCache())
        assertTrue(sessionManager.isAuthenticated())

        repo.logout()
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
    }

    // MARK: - PaymentsRepository

    @Test
    fun `PaymentsRepository getPaymentMethods GETs customer payment-methods with the bearer token and decodes the cards`() = runTest {
        enqueueJson("""[{"payment_method_id":"pm_1UGG","card_brand":"visa","card_last4":"4242","card_exp_month":2,"card_exp_year":2044}]""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val methods = repo.getPaymentMethods()

        assertEquals(
            listOf(SavedPaymentMethod("pm_1UGG", "visa", "4242", cardExpMonth = 2, cardExpYear = 2044)),
            methods,
        )
        val recorded = mockWebServer.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v2/customer/payment-methods/", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
    }

    @Test
    fun `PaymentsRepository payWithStoredPaymentMethod POSTs payments with the stored card and no save or auto top-up flags`() = runTest {
        enqueueJson("""{"detail":"ok","data":{"order_id":"o9"}}""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val response = repo.payWithStoredPaymentMethod("pm_1UGG", PaymentRequest.Type.TOP_UP, 42, "8901")

        assertEquals("o9", response.transaction?.orderId)
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v2/payments/", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("pm_1UGG", body["stored_payment_method"]?.jsonPrimitive?.content)
        assertEquals("top-up", body["type"]?.jsonPrimitive?.content)
        assertEquals("8901", body["iccid"]?.jsonPrimitive?.content)
        assertEquals("42", body["package_type_id"]?.jsonPrimitive?.content)
        assertEquals("stripe_intent", body["payment_method"]?.jsonPrimitive?.content)
        assertEquals("u@e.com", body["customer"]?.jsonObject?.get("email")?.jsonPrimitive?.content)
        assertFalse(body.containsKey("auto_top_up"))
        assertFalse(body.containsKey("save_payment_method"))
        assertFalse(body.containsKey("loyalty_points_amount"))
        assertFalse(body.containsKey("discount_code"))
    }

    @Test
    fun `PaymentsRepository payWithStoredPaymentMethod with a discount code sends discount_code and still no save or auto top-up flags`() = runTest {
        enqueueJson("""{"detail":"ok","data":{"order_id":"o10"}}""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val response = repo.payWithStoredPaymentMethod("pm_1UGG", PaymentRequest.Type.BUY, 42, discountCode = "NAB30")

        assertEquals("o10", response.transaction?.orderId)
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v2/payments/", recorded.path)
        val body = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("NAB30", body["discount_code"]?.jsonPrimitive?.content)
        assertEquals("pm_1UGG", body["stored_payment_method"]?.jsonPrimitive?.content)
        assertEquals("stripe_intent", body["payment_method"]?.jsonPrimitive?.content)
        assertFalse(body.containsKey("auto_top_up"))
        assertFalse(body.containsKey("save_payment_method"))
    }

    @Test
    fun `PaymentsRepository getPaymentMethods with a discount code GETs only the eligible cards with the bearer token`() = runTest {
        enqueueJson("""[{"payment_method_id":"pm_nab","card_brand":"visa","card_last4":"1111","card_exp_month":5,"card_exp_year":2030}]""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val methods = repo.getPaymentMethods(discountCode = "NAB30")

        assertEquals(
            listOf(SavedPaymentMethod("pm_nab", "visa", "1111", cardExpMonth = 5, cardExpYear = 2030)),
            methods,
        )
        val recorded = mockWebServer.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/api/v2/customer/payment-methods/?discount_code=NAB30", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
    }

    @Test
    fun `PaymentsRepository getPaymentMethods with a blank discount code sends no query`() = runTest {
        enqueueJson("""[]""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        repo.getPaymentMethods(discountCode = "  ")

        assertEquals("/api/v2/customer/payment-methods/", mockWebServer.takeRequest().path)
    }

    @Test
    fun `PaymentsRepository deletePaymentMethod DELETEs the card and succeeds on 204`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        repo.deletePaymentMethod("pm_1UGG")

        val recorded = mockWebServer.takeRequest()
        assertEquals("DELETE", recorded.method)
        assertEquals("/api/v2/customer/payment-methods/pm_1UGG/", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
    }

    @Test
    fun `PaymentsRepository deletePaymentMethod surfaces a rejection as a NetworkError`() = runTest {
        enqueueJson("""{"detail":"Payment method not found."}""", code = 404)

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val error = runCatching { repo.deletePaymentMethod("pm_missing") }.exceptionOrNull() as SdkError.NetworkError

        assertEquals(404, error.statusCode)
        assertEquals("Payment method not found.", error.message)
    }

    @Test
    fun `PaymentsRepository createPaymentMethodSetupIntent POSTs an empty body and decodes the intent`() = runTest {
        enqueueJson("""{"client_secret":"seti_1ABC_secret_XYZ","customer_id":"cus_123","publishable_key":"pk_test_456"}""", code = 201)

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val intent = repo.createPaymentMethodSetupIntent()

        assertEquals("seti_1ABC_secret_XYZ", intent.clientSecret)
        assertEquals("cus_123", intent.customerId)
        assertEquals("pk_test_456", intent.publishableKey)
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v2/customer/payment-methods/setup-intent/", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
        assertEquals(0L, recorded.bodySize)
    }

    @Test
    fun `PaymentsRepository createPaymentMethodSetupIntent reports a missing publishable key as null`() = runTest {
        enqueueJson("""{"client_secret":"seti_1ABC_secret_XYZ","customer_id":"cus_123"}""", code = 201)

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val intent = repo.createPaymentMethodSetupIntent()

        assertEquals("seti_1ABC_secret_XYZ", intent.clientSecret)
        assertNull(intent.publishableKey)
    }

    @Test
    fun `PaymentsRepository verifyPaymentMethod POSTs the card and code and decodes the eligible card`() = runTest {
        enqueueJson("""{"payment_method_id":"pm_nab","card_brand":"visa","card_last4":"1111","card_exp_month":5,"card_exp_year":2030}""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val card = repo.verifyPaymentMethod("pm_nab", "NAB30")

        assertEquals(SavedPaymentMethod("pm_nab", "visa", "1111", cardExpMonth = 5, cardExpYear = 2030), card)
        val recorded = mockWebServer.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/api/v2/customer/payment-methods/verify/", recorded.path)
        assertEquals("Bearer tok", recorded.getHeader("authorization"))
        assertEquals("""{"payment_method_id":"pm_nab","discount_code":"NAB30"}""", recorded.body.readUtf8())
    }

    @Test
    fun `PaymentsRepository verifyPaymentMethod surfaces an ineligible card as a 400 NetworkError with the backend detail`() = runTest {
        enqueueJson("""{"detail":"This card is not eligible for the NAB offer."}""", code = 400)

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val error = runCatching { repo.verifyPaymentMethod("pm_other", "NAB30") }.exceptionOrNull() as SdkError.NetworkError

        assertEquals(400, error.statusCode)
        assertEquals("This card is not eligible for the NAB offer.", error.message)
    }

    @Test
    fun `PaymentsRepository verifyPaymentMethod surfaces an unattached card as a 404 NetworkError`() = runTest {
        enqueueJson("""{"detail":"Payment method not found."}""", code = 404)

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        val error = runCatching { repo.verifyPaymentMethod("pm_missing", "NAB30") }.exceptionOrNull() as SdkError.NetworkError

        assertEquals(404, error.statusCode)
        assertEquals("Payment method not found.", error.message)
    }

    @Test
    fun `PaymentsRepository a normal purchase body is unchanged by the stored card field`() = runTest {
        enqueueJson("""{"detail":"ok","data":{"order_id":"o1"}}""")

        val repo = PaymentsRepositoryImpl(apiService, sessionManager)
        repo.getPaymentIntent(
            PaymentRequest(
                type = PaymentRequest.Type.BUY,
                customer = CustomerDetails(email = "u@e.com"),
                packageTypeId = 42,
                paymentMethod = PaymentRequest.Method.STRIPE_INTENT,
                autoTopUp = false,
                savePaymentMethod = true,
            )
        )

        val body = Json.parseToJsonElement(mockWebServer.takeRequest().body.readUtf8()).jsonObject
        assertEquals(
            setOf("type", "customer", "package_type_id", "payment_method", "auto_top_up", "save_payment_method"),
            body.keys,
        )
        assertEquals(false, body["auto_top_up"]?.jsonPrimitive?.boolean)
        assertEquals(true, body["save_payment_method"]?.jsonPrimitive?.boolean)
    }

    // MARK: - CountryRepository

    @Test
    fun `CountryRepository getCountries hits countries endpoint`() = runTest {
        enqueueJson("""{"count":0,"next":null,"previous":null,"results":[]}""")

        val repo = CountryRepositoryImpl(apiService, SdkCache())
        val countries = repo.getCountries()
        assertEquals(0, countries.size)

        val recorded = mockWebServer.takeRequest()
        assertTrue("Expected /countries got ${recorded.path}", recorded.path!!.contains("/countries"))
    }

    @Test
    fun `CountryRepository getCountriesBy passes query params`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = CountryRepositoryImpl(apiService, SdkCache())
        repo.getCountriesBy(Destination(code = "US", name = "United States", region = "NA", slug = null))

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("country_code=US"))
    }

    @Test
    fun `CountryRepository search hits search endpoint with query`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = CountryRepositoryImpl(apiService, SdkCache())
        repo.search("canada")

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/search"))
        assertTrue(recorded.path!!.contains("search_term=canada"))
    }

    // MARK: - PackagesRepository

    @Test
    fun `PackagesRepository getPackages hits packages endpoint`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = PackagesRepositoryImpl(apiService, SdkCache())
        repo.getPackages(Destination(code = "US", name = null, region = null, slug = "united-states"))

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/packages"))
        assertFalse(recorded.path!!.contains("promo_code"))
    }

    @Test
    fun `PackagesRepository getPackages with a promo code sends promo_code`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = PackagesRepositoryImpl(apiService, SdkCache())
        repo.getPackages(Destination(code = "US", name = null, region = null, slug = "united-states"), promoCode = "NAB30")

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/packages"))
        assertTrue(recorded.path!!.contains("country_name_slug=united-states"))
        assertTrue(recorded.path!!.contains("promo_code=NAB30"))
    }

    @Test
    fun `PackagesRepository getPackages with a blank promo code sends no promo_code`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = PackagesRepositoryImpl(apiService, SdkCache())
        repo.getPackages(Destination(code = "US", name = null, region = null, slug = "united-states"), promoCode = " ")

        assertFalse(mockWebServer.takeRequest().path!!.contains("promo_code"))
    }

    @Test
    fun `PackagesRepository promo and plain package reads keep separate cache entries`() = runTest {
        enqueueJson("""{"count":1,"results":[]}""")
        enqueueJson("""{"count":2,"results":[]}""")
        enqueueJson("""{"count":0,"results":[]}""")
        enqueueJson("""{"count":0,"results":[]}""")
        val repo = PackagesRepositoryImpl(apiService, SdkCache())
        val destination = Destination(code = "US", name = null, region = null, slug = "united-states")

        val plainPage = repo.getPackagesPage(destination)
        val promoPage = repo.getPackagesPage(destination, promoCode = "NAB30")
        repo.getPackages(destination)
        repo.getPackages(destination, promoCode = "NAB30")

        assertEquals(1, plainPage.totalCount)
        assertEquals(2, promoPage.totalCount)
        assertEquals(4, mockWebServer.requestCount)
    }

    // MARK: - EsimRepository

    @Test
    fun `EsimRepository getEsims combines active and archived requests`() = runTest {
        // getEsims() fans out to getActiveEsims + getArchivedEsims
        enqueueJson("""{"count":0,"results":[]}""")
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = EsimRepositoryImpl(apiService, SdkCache())
        val esims = repo.getEsims()
        assertEquals(0, esims.size)
        assertEquals(2, mockWebServer.requestCount)

        val active = mockWebServer.takeRequest()
        val archived = mockWebServer.takeRequest()
        assertTrue(active.path!!.contains("/customer/esims"))
        assertTrue(archived.path!!.contains("/customer/esims"))
    }

    // MARK: - OrdersRepository

    @Test
    fun `OrdersRepository getOrderHistory hits orders endpoint`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = OrdersRepositoryImpl(apiService, SdkCache())
        val orders = repo.getOrderHistory()
        assertEquals(0, orders.size)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/customer/orders"))
    }

    @Test
    fun `OrdersRepository getOrderHistory with loyalty points adds used_points param`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = OrdersRepositoryImpl(apiService, SdkCache())
        repo.getOrderHistory(withLoyaltyPoints = true)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("used_points=true"))
    }

    // MARK: - PromoCodeRepository

    @Test
    fun `PromoCodeRepository getPromoCode hits promo_code endpoint`() = runTest {
        enqueueJson("""{"valid":true,"detail":"applied","discount_code":"SAVE","discount_percentage":0.1,"product_type":"esim"}""")

        val repo = PromoCodeRepositoryImpl(apiService, Json { ignoreUnknownKeys = true })
        val response = repo.getPromoCode()
        assertEquals(true, response.valid)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("promo_code"))
    }

    // MARK: - LoyaltyRepository

    @Test
    fun `LoyaltyRepository getLoyaltyBalance hits loyalty endpoint`() = runTest {
        enqueueJson("""
            {"total_loyalty_points":1500,"total_loyalty_points_detail":{"amount":"15.00","currency":{"symbol":"$","iso":"USD"}}}
        """.trimIndent())

        val repo = LoyaltyRepositoryImpl(apiService, SdkCache())
        val response = repo.getLoyaltyBalance()
        assertEquals(1500, response.totalLoyaltyPoints)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/customer/loyalty"))
    }

    // MARK: - NotificationRepository

    @Test
    fun `NotificationRepository getSettings hits notifications endpoint`() = runTest {
        enqueueJson("[]")

        val repo = NotificationRepositoryImpl(apiService)
        val settings = repo.getSettings()
        assertEquals(0, settings.size)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/customer/notifications"))
    }

    // MARK: - VouchersRepository

    @Test
    fun `VouchersRepository redeemVoucher hits voucher endpoint`() = runTest {
        enqueueJson("""{"redeemed":true,"redirect_url":"https://example.com/o?id=ord-1"}""")

        val repo = VouchersRepositoryImpl(apiService)
        val response = repo.redeemVoucher("CODE")
        assertTrue(response.isSuccess)
        val body = response.getOrNull()
        assertNotNull(body)
        assertEquals(true, body?.redeemed)
        assertEquals("ord-1", body?.orderUUID)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("voucher"))
    }

    // MARK: - VisaRewardsRepository

    @Test
    fun `VisaRewardsRepository getIframe hits promotions iframe endpoint`() = runTest {
        enqueueJson("""{"iframe_url":"https://visa.example.com","token":"t-1"}""")

        val repo = VisaRewardsRepositoryImpl(apiService)
        val response = repo.getIframe(isEU = false)
        assertEquals("https://visa.example.com", response.iframeUrl)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/customer/promotions/iframe"))
    }

    // MARK: - UserRepository

    @Test
    fun `UserRepository preferences are read and saved locally`() = runTest {
        val repo = UserRepositoryImpl(storage)
        repo.saveEsimSupportedPopupFlag(true)
        assertEquals(true, repo.getEsimSupportedPopupFlag())

        repo.saveEsimNotSupporterPopupFlag(true)
        assertEquals(true, repo.getEsimNotSupportedPopupFlag())
    }
}
