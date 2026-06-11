package io.esimplified.sdk.network

import io.esimplified.sdk.SdkConfig
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime

class SdkAuthInterceptorExtendedTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var fakeStorage: FakeSecureStorage
    private lateinit var sessionManager: DefaultSessionManager

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        fakeStorage = FakeSecureStorage()
        sessionManager = DefaultSessionManager(fakeStorage)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun buildClient(
        sessionManager: SessionManager = this.sessionManager,
        customHeaders: (() -> Map<String, String>)? = null,
        awsWafToken: String = "",
    ): OkHttpClient {
        val config = SdkConfig(
            environment = io.esimplified.sdk.SdkEnvironment.STAGING,
            clientName = "acme",
            clientId = "id",
            clientSecret = "secret",
            awsWafToken = awsWafToken,
            customHeadersProvider = customHeaders,
        )
        val interceptor = SdkAuthInterceptor(sessionManager, config)
        return OkHttpClient.Builder().addInterceptor(interceptor).build()
    }

    private fun authenticated(
        accessToken: String = "tok",
        preferredCurrency: String? = null,
        preferredLanguage: String? = null
    ) = Auth.Authenticated(
        user = Customer(
            id = "u-1",
            email = "u@e.com",
            preferredCurrency = preferredCurrency,
            preferredLanguage = preferredLanguage
        ),
        accessToken = accessToken,
        refreshToken = "ref",
        expires = LocalDateTime.now().plusHours(1)
    )

    // MARK: - customHeadersProvider

    @Test
    fun `customHeadersProvider headers are attached to requests`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = buildClient(customHeaders = {
            mapOf("X-Custom" to "v1", "X-AppCheck" to "appcheck-token")
        })

        client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        val recorded = mockWebServer.takeRequest()
        assertEquals("v1", recorded.getHeader("X-Custom"))
        assertEquals("appcheck-token", recorded.getHeader("X-AppCheck"))
    }

    @Test
    fun `customHeadersProvider accept-currency overrides user preference`() {
        sessionManager.save(authenticated(preferredCurrency = "USD"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = buildClient(customHeaders = {
            mapOf("accept-currency" to "OMR")
        })

        client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        val recorded = mockWebServer.takeRequest()
        assertEquals("OMR", recorded.getHeader("accept-currency"))
    }

    @Test
    fun `customHeadersProvider accept-language overrides user preference`() {
        sessionManager.save(authenticated(preferredLanguage = "en"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = buildClient(customHeaders = {
            mapOf("accept-language" to "ar")
        })

        client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        val recorded = mockWebServer.takeRequest()
        assertEquals("ar", recorded.getHeader("accept-language"))
    }

    @Test
    fun `customHeadersProvider exception is silently ignored`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = buildClient(customHeaders = {
            throw RuntimeException("bad provider")
        })

        // Should not throw — interceptor swallows custom headers errors
        client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        assertEquals(1, mockWebServer.requestCount)
    }

    // MARK: - x-auth-validation absence

    @Test
    fun `x-auth-validation header omitted when awsWafToken empty`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = buildClient(awsWafToken = "")

        client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        val recorded = mockWebServer.takeRequest()
        assertNull(recorded.getHeader("x-auth-validation"))
    }

    // MARK: - Onauthentication callback (custom override)

    @Test
    fun `failed refresh invokes custom onAuthenticationFailed callback`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))

        var callbackInvoked = false
        val custom = object : SessionManager by sessionManager {
            override fun onAuthenticationFailed() {
                callbackInvoked = true
                sessionManager.save(Auth.Unauthenticated)
            }
        }
        val client = buildClient(sessionManager = custom)

        try {
            client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        } catch (_: Exception) { }

        assertTrue("onAuthenticationFailed should have been invoked", callbackInvoked)
    }

    // MARK: - SdkError IOException compatibility

    @Test
    fun `SdkError is an IOException so OkHttp does not crash`() {
        val error: Exception = SdkError.AuthenticationRequired()
        assertTrue("Expected IOException for OkHttp compatibility", error is IOException)
    }

    @Test
    fun `SdkError NetworkError carries status code and message`() {
        val error = SdkError.NetworkError(statusCode = 422, message = "validation failed")
        assertEquals(422, error.statusCode)
        assertTrue(error.message!!.contains("422"))
        assertTrue(error.message!!.contains("validation failed"))
    }

    @Test
    fun `SdkError DecodingError preserves cause`() {
        val cause = RuntimeException("missing field 'iccid'")
        val error = SdkError.DecodingError(cause)
        assertEquals(cause, error.cause)
    }

    // MARK: - Auth state preserved on non-401 errors

    @Test
    fun `5xx response does not trigger refresh and preserves session`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val client = buildClient()
        val response = client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        assertEquals(500, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertTrue(sessionManager.isAuthenticated())
    }

    @Test
    fun `4xx non-auth response does not trigger refresh`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":"validation"}"""))

        val client = buildClient()
        val response = client.newCall(Request.Builder().url(mockWebServer.url("/api/x")).build()).execute()
        assertEquals(422, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertTrue(sessionManager.isAuthenticated())
    }
}
