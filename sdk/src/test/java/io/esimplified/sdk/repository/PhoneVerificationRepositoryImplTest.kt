package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.PhoneOtpChannel
import io.esimplified.sdk.network.ApiErrorCode
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkError
import io.esimplified.sdk.repository.impl.PhoneVerificationRepositoryImpl
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

class PhoneVerificationRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: PhoneVerificationRepositoryImpl

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        val apiService: ApiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
        repository = PhoneVerificationRepositoryImpl(apiService)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // region Sending a code
    @Test
    fun `sending a code posts the number and channel`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"phone_number":"+27821234567","channel":"whatsapp"}""")
        )

        val response = repository.sendCode("+27 82 123 4567", PhoneOtpChannel.WHATSAPP)

        val request = mockWebServer.takeRequest()
        assertEquals("/api/v2/customer/phone/otp/", request.path)
        assertEquals("""{"phone_number":"+27 82 123 4567","channel":"whatsapp"}""", request.body.readUtf8())
        assertEquals("+27821234567", response.phoneNumber)
        assertEquals(PhoneOtpChannel.WHATSAPP, response.channel)
    }

    @Test
    fun `a number on another account surfaces the api code`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{"code":"phone_already_verified","detail":"Already verified elsewhere."}""")
        )

        val failure = runCatching { repository.sendCode("+27821234567", PhoneOtpChannel.SMS) }.exceptionOrNull()

        val error = failure as SdkError.NetworkError
        assertEquals(409, error.statusCode)
        assertTrue(error.hasApiCode(ApiErrorCode.PHONE_ALREADY_VERIFIED))
        assertEquals("Already verified elsewhere.", error.message)
    }
    // endregion

    // region Verifying a code
    @Test
    fun `verifying a code posts it and returns the verified number`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"phone_number":"+27821234567","phone_verified":true}""")
        )

        val response = repository.verifyCode("123456")

        val request = mockWebServer.takeRequest()
        assertEquals("/api/v2/customer/phone/otp/verify/", request.path)
        assertEquals("""{"code":"123456"}""", request.body.readUtf8())
        assertTrue(response.phoneVerified)
        assertEquals("+27821234567", response.phoneNumber)
    }

    @Test
    fun `a wrong code surfaces the api code`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"code":"invalid_code","detail":"Wrong code."}""")
        )

        val failure = runCatching { repository.verifyCode("000000") }.exceptionOrNull() as SdkError.NetworkError

        assertTrue(failure.hasApiCode(ApiErrorCode.INVALID_CODE))
    }
    // endregion
}
