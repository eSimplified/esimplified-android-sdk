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

        val repo = AuthRepositoryImpl(apiService, sessionManager, storage)
        val customer = repo.login("u2@e.com", "p")

        assertEquals("u-2", customer.id)
        assertEquals("new-tok", sessionManager.getAccessToken())
        assertTrue(sessionManager.isAuthenticated())

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/auth/token"))
    }

    @Test
    fun `AuthRepository logout clears session`() = runTest {
        val repo = AuthRepositoryImpl(apiService, sessionManager, storage)
        assertTrue(sessionManager.isAuthenticated())

        repo.logout()
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
    }

    // MARK: - CountryRepository

    @Test
    fun `CountryRepository getCountries hits countries endpoint`() = runTest {
        enqueueJson("""{"count":0,"next":null,"previous":null,"results":[]}""")

        val repo = CountryRepositoryImpl(apiService)
        val countries = repo.getCountries()
        assertEquals(0, countries.size)

        val recorded = mockWebServer.takeRequest()
        assertTrue("Expected /countries got ${recorded.path}", recorded.path!!.contains("/countries"))
    }

    @Test
    fun `CountryRepository getCountriesBy passes query params`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = CountryRepositoryImpl(apiService)
        repo.getCountriesBy(Destination(code = "US", name = "United States", region = "NA", slug = null))

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("country_code=US"))
    }

    @Test
    fun `CountryRepository search hits search endpoint with query`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = CountryRepositoryImpl(apiService)
        repo.search("canada")

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/search"))
        assertTrue(recorded.path!!.contains("search_term=canada"))
    }

    // MARK: - PackagesRepository

    @Test
    fun `PackagesRepository getPackages hits packages endpoint`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = PackagesRepositoryImpl(apiService)
        repo.getPackages(Destination(code = "US", name = null, region = null, slug = "united-states"))

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/packages"))
    }

    // MARK: - EsimRepository

    @Test
    fun `EsimRepository getEsims combines active and archived requests`() = runTest {
        // getEsims() fans out to getActiveEsims + getArchivedEsims
        enqueueJson("""{"count":0,"results":[]}""")
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = EsimRepositoryImpl(apiService)
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

        val repo = OrdersRepositoryImpl(apiService)
        val orders = repo.getOrderHistory()
        assertEquals(0, orders.size)

        val recorded = mockWebServer.takeRequest()
        assertTrue(recorded.path!!.contains("/customer/orders"))
    }

    @Test
    fun `OrdersRepository getOrderHistory with loyalty points adds used_points param`() = runTest {
        enqueueJson("""{"count":0,"results":[]}""")

        val repo = OrdersRepositoryImpl(apiService)
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

        val repo = LoyaltyRepositoryImpl(apiService)
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
