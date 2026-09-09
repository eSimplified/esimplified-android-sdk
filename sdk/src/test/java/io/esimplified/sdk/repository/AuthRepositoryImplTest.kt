package io.esimplified.sdk.repository

import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.impl.AuthRepositoryImpl
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

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        fakeStorage = FakeSecureStorage()
        sessionManager = DefaultSessionManager(fakeStorage)

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val client = OkHttpClient.Builder().build()
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/").toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()

        authRepository = AuthRepositoryImpl(apiService, sessionManager, fakeStorage)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    private fun seedAuthenticatedSession(refreshToken: String) {
        sessionManager.save(
            Auth.Authenticated(
                user = Customer(id = "user-123", email = "test@example.com"),
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
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"updated\": true}")
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
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"updated\": true}")
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
}
