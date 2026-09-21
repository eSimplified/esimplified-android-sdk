package io.esimplified.sdk.repository

import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.SdkLog
import io.esimplified.sdk.SdkLogLevel
import io.esimplified.sdk.SdkLogger
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.AuthRepositoryImpl
import io.esimplified.sdk.repository.impl.EsimRepositoryImpl
import io.esimplified.sdk.repository.impl.OrdersRepositoryImpl
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create
import java.time.LocalDateTime

class AuthRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeStorage: FakeSecureStorage
    private lateinit var sessionManager: DefaultSessionManager
    private lateinit var apiService: ApiService
    private lateinit var authRepository: AuthRepositoryImpl
    private lateinit var cache: SdkCache

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        fakeStorage = FakeSecureStorage()
        sessionManager = DefaultSessionManager(fakeStorage)
        cache = SdkCache()

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val client = OkHttpClient.Builder().build()
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()

        authRepository = AuthRepositoryImpl(apiService, sessionManager, fakeStorage, cache)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        SdkLog.resetForTesting()
    }

    private fun seedAuthenticatedSession(refreshToken: String, referralCode: String? = null) {
        sessionManager.save(
            Auth.Authenticated(
                user = Customer(id = "user-123", email = "test@example.com", referralCode = referralCode),
                accessToken = "old-access-token",
                refreshToken = refreshToken,
                expires = LocalDateTime.now().plusSeconds(3600)
            )
        )
    }

    @Test
    fun `loginWithRefreshToken preserves the stored refresh token when the backend returns none`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "access_token": "new-access-token",
                        "expires_in": 3600,
                        "user": { "customer_id": "user-123", "email": "test@example.com" }
                    }
                    """.trimIndent()
                )
        )

        authRepository.loginWithRefreshToken("original-refresh-token")

        assertEquals("original-refresh-token", sessionManager.getRefreshToken())
    }

    @Test
    fun `loginWithRefreshToken adopts a rotated refresh token when the backend returns one`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "access_token": "new-access-token",
                        "refresh_token": "rotated-refresh-token",
                        "expires_in": 3600,
                        "user": { "customer_id": "user-123", "email": "test@example.com" }
                    }
                    """.trimIndent()
                )
        )

        authRepository.loginWithRefreshToken("original-refresh-token")

        assertEquals("rotated-refresh-token", sessionManager.getRefreshToken())
    }

    // region A refresh the server did not reject keeps the session
    @Test
    fun `loginWithRefreshToken keeps the session when the backend returns 500`() = runTest {
        assertRefreshFailureKeepsTheSession(statusCode = 500)
    }

    @Test
    fun `loginWithRefreshToken keeps the session when the backend returns 502`() = runTest {
        assertRefreshFailureKeepsTheSession(statusCode = 502)
    }

    @Test
    fun `loginWithRefreshToken keeps the session when the backend returns 503`() = runTest {
        assertRefreshFailureKeepsTheSession(statusCode = 503)
    }

    @Test
    fun `loginWithRefreshToken keeps the session when the backend returns 429`() = runTest {
        assertRefreshFailureKeepsTheSession(statusCode = 429)
    }

    @Test
    fun `loginWithRefreshToken keeps the session when a 403 carries no grant rejection`() = runTest {
        assertRefreshFailureKeepsTheSession(statusCode = 403, body = "<html>blocked by the edge</html>")
    }

    @Test
    fun `loginWithRefreshToken keeps the session when the response carries no access token`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"expires_in":3600}""")
        )

        val thrown = runCatching { authRepository.loginWithRefreshToken("original-refresh-token") }.exceptionOrNull()

        assertFalse("Expected the session to survive", thrown is InvalidRefreshTokenException)
        val state = sessionManager.getAuthState()
        assertTrue(state is Auth.Authenticated)
        assertEquals("old-access-token", (state as Auth.Authenticated).accessToken)
    }

    @Test
    fun `loginWithRefreshToken reuses the stored customer when the response omits one`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":"new-access-token","refresh_token":"rotated-refresh-token","expires_in":3600}"""
            )
        )

        val customer = authRepository.loginWithRefreshToken("original-refresh-token")

        assertEquals("user-123", customer.id)
        val state = sessionManager.getAuthState() as Auth.Authenticated
        assertEquals("new-access-token", state.accessToken)
        assertEquals("rotated-refresh-token", state.refreshToken)
        assertEquals("user-123", state.user.id)
    }
    // endregion

    // region A refresh the server did reject ends the session
    @Test
    fun `loginWithRefreshToken ends the session when the grant is rejected`() = runTest {
        seedAuthenticatedSession(refreshToken = "burned-refresh-token")
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}""")
        )

        val thrown = runCatching { authRepository.loginWithRefreshToken("burned-refresh-token") }.exceptionOrNull()

        assertTrue("Expected InvalidRefreshTokenException, got $thrown", thrown is InvalidRefreshTokenException)
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
    }

    @Test
    fun `loginWithRefreshToken ends the session on a 401`() = runTest {
        seedAuthenticatedSession(refreshToken = "burned-refresh-token")
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val thrown = runCatching { authRepository.loginWithRefreshToken("burned-refresh-token") }.exceptionOrNull()

        assertTrue("Expected InvalidRefreshTokenException, got $thrown", thrown is InvalidRefreshTokenException)
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
    }
    // endregion

    private suspend fun assertRefreshFailureKeepsTheSession(statusCode: Int, body: String = "") {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        mockWebServer.enqueue(MockResponse().setResponseCode(statusCode).setBody(body))

        val thrown = runCatching { authRepository.loginWithRefreshToken("original-refresh-token") }.exceptionOrNull()

        assertTrue("Expected the failure to surface", thrown != null)
        assertFalse(
            "HTTP $statusCode on a refresh is not the server rejecting the session",
            thrown is InvalidRefreshTokenException
        )
        val state = sessionManager.getAuthState()
        assertTrue("HTTP $statusCode must not log the user out", state is Auth.Authenticated)
        assertEquals("original-refresh-token", (state as Auth.Authenticated).refreshToken)
    }

    private fun simulateInterceptorRotationDuringPreferencesCall(tokenResponseBody: String) {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("auth/token") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(tokenResponseBody)

                    path.contains("customer/preferences") -> {
                        val current = sessionManager.getAuthState()
                        if (current is Auth.Authenticated) {
                            sessionManager.save(
                                current.copy(
                                    accessToken = "interceptor-rotated-access-token",
                                    refreshToken = "interceptor-rotated-refresh-token"
                                )
                            )
                        }
                        MockResponse()
                            .setResponseCode(200)
                            .setBody("""{ "customer_id": "user-123", "loyalty_provider": "mokafaa" }""")
                    }

                    path.contains("api/v2/customer/") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{ "customer_id": "user-123", "email": "test@example.com" }""")

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @Test
    fun `login reads loyalty fields from the auth response without calling preferences`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "access_token": "login-access-token",
                        "refresh_token": "login-refresh-token",
                        "expires_in": 3600,
                        "user": {
                            "customer_id": "user-123",
                            "email": "test@example.com",
                            "loyalty_provider": "mokafaa",
                            "mokafaa_enrollment": { "state": "completed", "session_expires_at": null }
                        }
                    }
                    """.trimIndent()
                )
        )

        val user = authRepository.login("test@example.com", "password")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals("mokafaa", user.loyaltyProvider)
        assertEquals("completed", user.mokafaaEnrollment?.state)
        val savedUser = (sessionManager.getAuthState() as Auth.Authenticated).user
        assertEquals("mokafaa", savedUser.loyaltyProvider)
        assertEquals("completed", savedUser.mokafaaEnrollment?.state)
        assertEquals("login-refresh-token", sessionManager.getRefreshToken())
    }

    @Test
    fun `loginWithRefreshToken reads loyalty fields from the auth response without calling preferences`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "access_token": "new-access-token",
                        "refresh_token": "rotated-refresh-token",
                        "expires_in": 3600,
                        "user": {
                            "customer_id": "user-123",
                            "email": "test@example.com",
                            "loyalty_provider": "mokafaa",
                            "mokafaa_enrollment": { "state": "pending", "session_expires_at": "2026-08-12T10:00:00Z" }
                        }
                    }
                    """.trimIndent()
                )
        )

        authRepository.loginWithRefreshToken("original-refresh-token")

        assertEquals(1, mockWebServer.requestCount)
        assertEquals("rotated-refresh-token", sessionManager.getRefreshToken())
        val savedUser = (sessionManager.getAuthState() as Auth.Authenticated).user
        assertEquals("mokafaa", savedUser.loyaltyProvider)
        assertEquals("pending", savedUser.mokafaaEnrollment?.state)
        assertEquals("2026-08-12T10:00:00Z", savedUser.mokafaaEnrollment?.sessionExpiresAt)
    }

    @Test
    fun `updateCustomerProfile can send a phone number on its own`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile(
            """{ "customer_id": "user-123", "email": "test@example.com" }"""
        )

        authRepository.updateCustomerProfile(phoneNumber = "+27831234567")

        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"phone_number\":\"+27831234567\""))
        assertFalse(body.contains("\"password\""))
        assertFalse(body.contains("\"new_email\""))
        assertEquals(
            "+27831234567",
            (sessionManager.getAuthState() as Auth.Authenticated).user.phoneNumber
        )
    }

    @Test
    fun `updateCustomerProfile leaves the stored email alone when none is supplied`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile(
            """{ "customer_id": "user-123", "email": "test@example.com" }"""
        )

        authRepository.updateCustomerProfile(firstName = "Kieran")

        assertEquals(
            "test@example.com",
            (sessionManager.getAuthState() as Auth.Authenticated).user.email
        )
    }

    @Test
    fun `getUser does not clobber a refresh token rotated during the request`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        simulateInterceptorRotationDuringPreferencesCall(tokenResponseBody = "{}")

        authRepository.getUser()

        assertEquals("interceptor-rotated-refresh-token", sessionManager.getRefreshToken())
    }

    private fun serveProfile(profileBody: String, preferencesBody: String = """{ "customer_id": "user-123" }""") {
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("customer/preferences") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(preferencesBody)

                    path.contains("customer/edit") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{"updated": true, "success": true}""")

                    path.contains("api/v2/customer/") -> MockResponse()
                        .setResponseCode(200)
                        .setBody(profileBody)

                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @Test
    fun `fetchProfile requests the customer endpoint with a trailing slash`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile("""{ "customer_id": "user-123", "referral_code": "FETCH1" }""")

        val user = authRepository.fetchProfile()

        assertEquals("FETCH1", user?.referralCode)
        val paths = (1..mockWebServer.requestCount).map { mockWebServer.takeRequest().path }
        assertTrue(paths.contains("/api/v2/customer/"))
    }

    @Test
    fun `fetchProfile reads a unique_referral_code payload into the referral code`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile("""{ "customer_id": "user-123", "unique_referral_code": "ALIAS9" }""")

        assertEquals("ALIAS9", authRepository.fetchProfile()?.referralCode)
        assertEquals(
            "ALIAS9",
            (sessionManager.getAuthState() as Auth.Authenticated).user.referralCode
        )
    }

    @Test
    fun `fetchProfile keeps the session referral code when the payload carries neither key`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token", referralCode = "KEEPME")
        serveProfile("""{ "customer_id": "user-123", "email": "test@example.com" }""")

        assertEquals("KEEPME", authRepository.fetchProfile()?.referralCode)
        assertEquals(
            "KEEPME",
            (sessionManager.getAuthState() as Auth.Authenticated).user.referralCode
        )
    }

    @Test
    fun `getUser still returns the profile and keeps the loyalty provider merge`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile(
            profileBody = """{ "customer_id": "user-123", "referral_code": "LEGACY1" }""",
            preferencesBody = """{ "customer_id": "user-123", "loyalty_provider": "mokafaa" }"""
        )

        val user = authRepository.getUser()

        assertEquals("LEGACY1", user?.referralCode)
        assertEquals("mokafaa", user?.loyaltyProvider)
    }

    @Test
    fun `updatePreferences leaves the session customer holding the referral code`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile(
            profileBody = """
                {
                    "customer_id": "user-123",
                    "referral_code": "AFTERPATCH",
                    "signed_in_with_provider": true,
                    "receive_marketing_email": false
                }
            """.trimIndent(),
            preferencesBody = """{ "customer_id": "user-123", "unique_referral_code": "AFTERPATCH" }"""
        )

        val returned = authRepository.updatePreferences(
            preferredLanguage = "ar",
            preferredCurrency = "SAR"
        )

        assertEquals("AFTERPATCH", returned.referralCode)
        assertEquals("ar", returned.preferredLanguage)
        assertEquals("SAR", returned.preferredCurrency)
        val savedUser = (sessionManager.getAuthState() as Auth.Authenticated).user
        assertEquals("AFTERPATCH", savedUser.referralCode)
        assertEquals(true, savedUser.signedInWithProvider)
        assertEquals(false, savedUser.receiveMarketingEmail)
        assertEquals("ar", savedUser.preferredLanguage)
        assertEquals("SAR", savedUser.preferredCurrency)
    }

    @Test
    fun `updatePreferences falls back to language and currency when the re-fetch fails`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token", referralCode = "FALLBACK")
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("customer/preferences") -> MockResponse()
                        .setResponseCode(200)
                        .setBody("""{ "customer_id": "user-123", "unique_referral_code": "FALLBACK" }""")

                    else -> MockResponse().setResponseCode(500)
                }
            }
        }

        authRepository.updatePreferences(preferredLanguage = "fr", preferredCurrency = "EUR")

        val savedUser = (sessionManager.getAuthState() as Auth.Authenticated).user
        assertEquals("fr", savedUser.preferredLanguage)
        assertEquals("EUR", savedUser.preferredCurrency)
        assertEquals("FALLBACK", savedUser.referralCode)
    }

    @Test
    fun `updateCustomerProfile re-fetches the full profile after a partial edit response`() = runTest {
        seedAuthenticatedSession(refreshToken = "original-refresh-token")
        serveProfile(
            """{ "customer_id": "user-123", "email": "test@example.com", "referral_code": "EDIT7" }"""
        )

        authRepository.updateCustomerProfile(firstName = "Kieran")

        val savedUser = (sessionManager.getAuthState() as Auth.Authenticated).user
        assertEquals("EDIT7", savedUser.referralCode)
        assertEquals("Kieran", savedUser.firstName)
    }

    // region Logout
    @Test
    fun `logout clears the cached eSIM list so the next customer is not served the previous one's`() = runTest {
        seedAuthenticatedSession(refreshToken = "first-user-refresh-token")
        enqueueEsimList(iccid = "first-user-iccid")
        val esimRepository = EsimRepositoryImpl(apiService, cache)
        assertEquals("first-user-iccid", esimRepository.getActiveEsims().single().iccid)

        authRepository.logout()

        enqueueEsimList(iccid = "second-user-iccid")
        assertEquals("second-user-iccid", esimRepository.getActiveEsims().single().iccid)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `logout clears the cached order history so the next customer is not served the previous one's`() = runTest {
        seedAuthenticatedSession(refreshToken = "first-user-refresh-token")
        enqueueOrderHistory(orderNumber = 111)
        val ordersRepository = OrdersRepositoryImpl(apiService, cache)
        assertEquals(111, ordersRepository.getOrderHistory().single().orderNumber)

        authRepository.logout()

        enqueueOrderHistory(orderNumber = 222)
        assertEquals(222, ordersRepository.getOrderHistory().single().orderNumber)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `logout leaves the session unauthenticated`() = runTest {
        seedAuthenticatedSession(refreshToken = "first-user-refresh-token")

        authRepository.logout()

        assertFalse(sessionManager.isAuthenticated())
        assertEquals(Auth.Unauthenticated, sessionManager.getAuthState())
    }

    private fun enqueueEsimList(iccid: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"count":1,"results":[{"iccid":"$iccid","esim_name":null}]}""")
        )
    }

    private fun enqueueOrderHistory(orderNumber: Int) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"count":1,"results":[{"order_number":$orderNumber,"final_price":"10.00",""" +
                        """"purchase_price":"10.00","discount_amount":"0.00","package_type_id":1}]}"""
                )
        )
    }
    // endregion


    // region Logging
    @Test
    fun `the login path never logs the customer's email address`() = runTest {
        val lines = mutableListOf<String>()
        SdkLog.delegate = SdkLogger { _: SdkLogLevel, message: String, _: Throwable? -> lines += message }

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                        "access_token": "an-access-token",
                        "refresh_token": "a-refresh-token",
                        "expires_in": 3600,
                        "user": { "customer_id": "user-123", "email": "private@example.com" }
                    }
                    """.trimIndent()
                )
        )

        authRepository.login("private@example.com", "hunter2")

        assertTrue("Expected the login path to log something", lines.isNotEmpty())
        assertFalse(lines.any { it.contains("private@example.com") })
        assertFalse(lines.any { it.contains("hunter2") })
        assertFalse(lines.any { it.contains("an-access-token") })
        assertFalse(lines.any { it.contains("a-refresh-token") })
    }

    @Test
    fun `a rejected login logs the status code but not the response body`() = runTest {
        val lines = mutableListOf<String>()
        SdkLog.delegate = SdkLogger { _: SdkLogLevel, message: String, _: Throwable? -> lines += message }

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"detail":"No active account found with private@example.com"}""")
        )

        runCatching { authRepository.login("private@example.com", "hunter2") }

        assertTrue(lines.any { it.contains("401") })
        assertFalse(lines.any { it.contains("private@example.com") })
    }
    // endregion

}
