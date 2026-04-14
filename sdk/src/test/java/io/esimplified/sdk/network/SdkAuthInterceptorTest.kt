package io.esimplified.sdk.network

import io.esimplified.sdk.SdkConfig
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class SdkAuthInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeStorage: FakeSecureStorage
    private lateinit var sessionManager: DefaultSessionManager
    private lateinit var interceptor: SdkAuthInterceptor
    private lateinit var client: OkHttpClient
    private lateinit var config: SdkConfig

    // With unitTests.isReturnDefaultValues = true, android.util.Base64.encodeToString returns null,
    // so the Basic auth header becomes "Basic null". We use this constant in assertions.
    private val expectedBasicAuth = "Basic null"

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        fakeStorage = FakeSecureStorage()
        sessionManager = DefaultSessionManager(fakeStorage)
        config = SdkConfig(
            baseUrl = mockWebServer.url("/").toString().trimEnd('/'),
            clientId = "test-client",
            clientSecret = "test-secret",
            awsWafToken = "test-waf-token"
        )
        interceptor = SdkAuthInterceptor(sessionManager = sessionManager, config = config)
        client = OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun createTestAuth(
        accessToken: String = "test-access-token",
        refreshToken: String = "test-refresh-token",
        preferredCurrency: String? = null,
        preferredLanguage: String? = null
    ): Auth.Authenticated {
        return Auth.Authenticated(
            user = Customer(
                id = "user-123",
                email = "test@example.com",
                firstName = "John",
                lastName = "Doe",
                fullName = "John Doe",
                phoneNumber = "+1234567890",
                externalReference = "ext-ref",
                referralCode = "REF123",
                preferredCurrency = preferredCurrency,
                preferredLanguage = preferredLanguage
            ),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expires = LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        )
    }

    @Test
    fun `unauthenticated request uses Basic auth header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        val authHeader = recorded.getHeader("authorization")
        assertNotNull(authHeader)
        assertTrue(
            "Expected Basic auth header but got: $authHeader",
            authHeader!!.startsWith("Basic ")
        )
    }

    @Test
    fun `authenticated request uses Bearer token header`() {
        sessionManager.save(createTestAuth(accessToken = "my-bearer-token"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("Bearer my-bearer-token", recorded.getHeader("authorization"))
    }

    @Test
    fun `request includes accept-currency header from user preferences`() {
        sessionManager.save(createTestAuth(preferredCurrency = "EUR"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("EUR", recorded.getHeader("accept-currency"))
    }

    @Test
    fun `request includes accept-language header from user preferences`() {
        sessionManager.save(createTestAuth(preferredLanguage = "fr"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("fr", recorded.getHeader("accept-language"))
    }

    @Test
    fun `request includes aws waf token header`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        assertEquals("test-waf-token", recorded.getHeader("x-auth-validation"))
    }

    @Test
    fun `401 response triggers token refresh`() {
        sessionManager.save(createTestAuth())

        // First response is 401
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        // Refresh token response
        val tokenJson = """
            {
                "access_token": "new-access-token",
                "refresh_token": "new-refresh-token",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson))
        // Retry response
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        client.newCall(request).execute()

        // Verify 3 requests were made: original, refresh, retry
        assertEquals(3, mockWebServer.requestCount)

        // First request: original with Bearer token
        val originalRequest = mockWebServer.takeRequest()
        assertEquals("Bearer test-access-token", originalRequest.getHeader("authorization"))

        // Second request: refresh token request to auth/token/
        val refreshRequest = mockWebServer.takeRequest()
        assertTrue(refreshRequest.path!!.contains("auth/token"))
        assertTrue(
            "Expected Basic auth for refresh but got: ${refreshRequest.getHeader("authorization")}",
            refreshRequest.getHeader("authorization")!!.startsWith("Basic ")
        )
    }

    @Test
    fun `successful refresh retries original request with new token`() {
        sessionManager.save(createTestAuth())

        // 401 on original
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        // Successful refresh
        val tokenJson = """
            {
                "access_token": "refreshed-token",
                "refresh_token": "refreshed-refresh",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(tokenJson))
        // Retry succeeds
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"result":"ok"}"""))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        val response = client.newCall(request).execute()

        assertEquals(200, response.code)

        // Skip original and refresh requests
        mockWebServer.takeRequest()
        mockWebServer.takeRequest()

        // Third request should use the new token
        val retryRequest = mockWebServer.takeRequest()
        assertEquals("Bearer refreshed-token", retryRequest.getHeader("authorization"))
    }

    @Test
    fun `failed refresh returns error response`() {
        sessionManager.save(createTestAuth())

        // 401 on original
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        // Refresh fails
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        val request = Request.Builder().url(mockWebServer.url("/api/test")).build()
        val response = client.newCall(request).execute()

        // Should return the failed refresh response
        assertEquals(400, response.code)
        // Only 2 requests: original + failed refresh (no retry)
        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `auth token endpoint uses Basic auth even when authenticated`() {
        sessionManager.save(createTestAuth(accessToken = "my-token"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val request = Request.Builder().url(mockWebServer.url("/auth/token/")).build()
        client.newCall(request).execute()

        val recorded = mockWebServer.takeRequest()
        val authHeader = recorded.getHeader("authorization")
        assertNotNull(authHeader)
        // auth/token endpoints keep Basic auth even when authenticated
        assertTrue(
            "Expected Basic auth for auth/token endpoint but got: $authHeader",
            authHeader!!.startsWith("Basic ")
        )
    }
}
