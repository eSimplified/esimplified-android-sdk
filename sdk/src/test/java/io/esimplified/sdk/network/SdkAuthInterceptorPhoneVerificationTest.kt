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

    @Test
    fun `a 403 with any other code still refreshes and retries`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":"forbidden","detail":"No."}"""))
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
