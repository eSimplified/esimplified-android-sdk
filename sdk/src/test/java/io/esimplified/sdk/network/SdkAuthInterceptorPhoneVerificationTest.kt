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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class SdkAuthInterceptorPhoneVerificationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var sessionManager: DefaultSessionManager
    private lateinit var client: OkHttpClient

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        sessionManager = DefaultSessionManager(FakeSecureStorage())
        val config = SdkConfig(
            environment = io.esimplified.sdk.SdkEnvironment.STAGING,
            clientName = "acme",
            clientId = "id",
            clientSecret = "secret",
        ).copy(baseUrlOverride = mockWebServer.url("/").toString().trimEnd('/'))
        client = OkHttpClient.Builder().addInterceptor(SdkAuthInterceptor(sessionManager, config)).build()
        sessionManager.save(
            Auth.Authenticated(
                user = Customer(id = "u-1"),
                accessToken = "tok",
                refreshToken = "ref",
                expires = LocalDateTime.now().plusHours(1),
            )
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // region Phone verification required
    @Test
    fun `a 403 that requires phone verification is handed to the caller without a refresh`() {
        val body = """{"code":"phone_verification_required","detail":"Verify your phone first."}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody(body))

        val response = client.newCall(Request.Builder().url(mockWebServer.url("/api/v2/customer/promotions/iframe/")).build()).execute()

        assertEquals(403, response.code)
        assertEquals(1, mockWebServer.requestCount)
        assertTrue(sessionManager.isAuthenticated())
        assertEquals("tok", sessionManager.getAccessToken())
        assertEquals(body, response.body?.string())
    }

    // endregion

    // region Server decisions carry a code
    @Test
    fun `a 403 with a code in the body is handed to the caller after one request and no refresh`() {
        val body = """{"code":"redemption_blocked","detail":"We can't verify this card right now."}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody(body))

        val response = client.newCall(Request.Builder().url(mockWebServer.url("/api/v2/customer/promotions/iframe/")).build()).execute()

        assertEquals(403, response.code)
        assertEquals("a server decision must not be replayed", 1, mockWebServer.requestCount)
        assertTrue(sessionManager.isAuthenticated())
        assertEquals("tok", sessionManager.getAccessToken())
        assertEquals(body, response.body?.string())
    }

    @Test
    fun `a 403 phone_range_burst on the OTP send is handed to the caller after one request and no refresh`() {
        assertCodedForbiddenIsNotReplayed(path = "/api/v2/phone/otp/", code = "phone_range_burst")
    }

    @Test
    fun `a 403 phone_change_locked on the OTP send is handed to the caller after one request and no refresh`() {
        assertCodedForbiddenIsNotReplayed(path = "/api/v2/phone/otp/", code = "phone_change_locked")
    }

    private fun assertCodedForbiddenIsNotReplayed(path: String, code: String) {
        val body = """{"detail":"We can't send a code to this number right now.","code":"$code"}"""
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody(body))

        val response = client.newCall(
            Request.Builder().url(mockWebServer.url(path)).post(okhttp3.RequestBody.create(null, "{}")).build()
        ).execute()

        assertEquals(403, response.code)
        assertEquals("a server decision must not be replayed", 1, mockWebServer.requestCount)
        assertTrue(sessionManager.isAuthenticated())
        assertEquals("tok", sessionManager.getAccessToken())
        assertEquals(code, ApiErrorMessage.code(response.body?.string()))
    }

    @Test
    fun `a 403 with no code in the body still refreshes and retries`() {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"detail":"You do not have permission to perform this action."}""")
        )
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"access_token":"new","refresh_token":"ref2","expires_in":3600}""")
        )
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val response = client.newCall(Request.Builder().url(mockWebServer.url("/api/v2/x")).build()).execute()

        assertEquals(200, response.code)
        assertEquals(3, mockWebServer.requestCount)
        assertEquals("new", sessionManager.getAccessToken())
    }
    // endregion
}
