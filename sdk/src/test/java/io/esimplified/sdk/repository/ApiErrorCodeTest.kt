package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiErrorCode
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkError
import io.esimplified.sdk.repository.impl.PromoCodeRepositoryImpl
import io.esimplified.sdk.repository.impl.VisaRewardsRepositoryImpl
import io.esimplified.sdk.repository.impl.VouchersRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class ApiErrorCodeTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var json: Json

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
        apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // region Code carried
    @Test
    fun `a 403 on the visa iframe carries the api code and the detail`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"code":"phone_verification_required","detail":"Verify your phone first."}""")
        )

        val failure = runCatching { VisaRewardsRepositoryImpl(apiService).getIframe(isEU = false) }.exceptionOrNull()

        val error = failure as SdkError.NetworkError
        assertEquals(403, error.statusCode)
        assertEquals("phone_verification_required", error.apiCode)
        assertTrue(error.hasApiCode(ApiErrorCode.PHONE_VERIFICATION_REQUIRED))
        assertEquals("Verify your phone first.", error.message)
    }

    @Test
    fun `a rejected voucher carries the api code`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"code":"phone_verification_required","detail":"Verify your phone first."}""")
        )

        val failure = VouchersRepositoryImpl(apiService).redeemVoucher("ABC").exceptionOrNull() as SdkError.NetworkError

        assertTrue(failure.hasApiCode(ApiErrorCode.PHONE_VERIFICATION_REQUIRED))
    }

    @Test
    fun `a rejected promo code carries the api code and status`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"code":"phone_verification_required","detail":"Verify your phone first.","valid":false}""")
        )

        val failure = runCatching { PromoCodeRepositoryImpl(apiService, json).addPromoCode("SAVE") }.exceptionOrNull()

        val error = failure as SdkError.NetworkError
        assertEquals(403, error.statusCode)
        assertTrue(error.hasApiCode(ApiErrorCode.PHONE_VERIFICATION_REQUIRED))
        assertEquals("Verify your phone first.", error.message)
    }
    // endregion

    // region No code
    @Test
    fun `a body without a code leaves apiCode null`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"Bad request."}"""))

        val failure = VouchersRepositoryImpl(apiService).redeemVoucher("ABC").exceptionOrNull() as SdkError.NetworkError

        assertNull(failure.apiCode)
        assertFalse(failure.hasApiCode(ApiErrorCode.INVALID_CODE))
    }

    @Test
    fun `an empty code is treated as no code`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"code":"","detail":"Bad request."}"""))

        val failure = VouchersRepositoryImpl(apiService).redeemVoucher("ABC").exceptionOrNull() as SdkError.NetworkError

        assertNull(failure.apiCode)
    }
    // endregion
}
