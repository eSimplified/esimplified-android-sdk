package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.network.ApiErrorCode
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.network.SdkError
import io.esimplified.sdk.repository.impl.AuthRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class AuthRepositoryVerifyEmailTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var authRepository: AuthRepositoryImpl

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val fakeStorage = FakeSecureStorage()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val apiService: ApiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
        authRepository = AuthRepositoryImpl(apiService, DefaultSessionManager(fakeStorage), fakeStorage, SdkCache())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // region Request body
    @Test
    fun `the emailed code is sent with only the email`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"email_verified":true}"""))

        authRepository.verifyEmail(email = "a@example.com", code = "482913")

        val request = mockWebServer.takeRequest()
        assertEquals("/api/v2/verify-email/", request.path)
        assertEquals("""{"email":"a@example.com","code":"482913"}""", request.body.readUtf8())
    }

    @Test
    fun `the link token is sent with the order and no code`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"email_verified":true}"""))

        authRepository.verifyEmail(email = "a@example.com", token = "tok", orderUUID = "order-1")

        assertEquals(
            """{"email":"a@example.com","email_verification_token":"tok","order_uuid":"order-1"}""",
            mockWebServer.takeRequest().body.readUtf8(),
        )
    }
    // endregion

    // region Failures
    @Test
    fun `a wrong code surfaces the api code`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"code":"invalid_code","detail":"That code is wrong."}""")
        )

        val failure = runCatching { authRepository.verifyEmail(email = "a@example.com", code = "000000") }
            .exceptionOrNull() as SdkError.NetworkError

        assertTrue(failure.hasApiCode(ApiErrorCode.INVALID_CODE))
        assertEquals("That code is wrong.", failure.message)
    }

    @Test
    fun `an expired code surfaces the api code`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"code":"code_expired","detail":"A new code was sent."}""")
        )

        val failure = runCatching { authRepository.verifyEmail(email = "a@example.com", code = "000000") }
            .exceptionOrNull() as SdkError.NetworkError

        assertTrue(failure.hasApiCode(ApiErrorCode.CODE_EXPIRED))
    }
    // endregion
}
