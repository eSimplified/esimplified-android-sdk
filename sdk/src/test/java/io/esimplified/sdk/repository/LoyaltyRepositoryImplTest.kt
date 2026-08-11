package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.MokafaaOtpInitiateRequest
import io.esimplified.sdk.model.MokafaaOtpValidateResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.LoyaltyApiException
import io.esimplified.sdk.repository.impl.LoyaltyRepositoryImpl
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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class LoyaltyRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: LoyaltyRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        val apiService = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<ApiService>()
        repository = LoyaltyRepositoryImpl(apiService)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `initiateMokafaaOtp sends purpose and platform and parses response`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"session-1","expires_at":"2026-06-11T10:05:00.000Z"}"""
            )
        )

        val response = repository.initiateMokafaaOtp(MokafaaOtpInitiateRequest.Purpose.ENROLLMENT)

        assertEquals("session-1", response.sessionId)
        assertEquals("2026-06-11T10:05:00.000Z", response.expiresAt)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/v2/loyalty/mokafaa/otp/initiate/", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""purpose":"enrollment""""))
        assertTrue(body.contains(""""platform":"android""""))
    }

    @Test
    fun `validateMokafaaOtp omits points for enrollment`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"session-1","status":"reversed"}"""
            )
        )

        val response = repository.validateMokafaaOtp(sessionId = "session-1", otp = "1234")

        assertEquals(MokafaaOtpValidateResponse.Status.REVERSED, response.status)
        assertNull(response.pointsRedeemed)
        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""session_id":"session-1""""))
        assertTrue(body.contains(""""otp":"1234""""))
        assertFalse(body.contains("points"))
    }

    @Test
    fun `validateMokafaaOtp sends points for checkout`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"session-1","status":"confirmed","points_redeemed":200}"""
            )
        )

        val response = repository.validateMokafaaOtp(sessionId = "session-1", otp = "1234", points = 200)

        assertEquals(MokafaaOtpValidateResponse.Status.CONFIRMED, response.status)
        assertEquals(200, response.pointsRedeemed)
        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""points":200"""))
    }

    @Test
    fun `validateMokafaaOtp sends package_type_id for checkout`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"session-1","status":"confirmed","points_redeemed":500}"""
            )
        )

        repository.validateMokafaaOtp(sessionId = "session-1", otp = "1234", points = 500, packageTypeId = 456)

        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(body.contains(""""package_type_id":456"""))
    }

    @Test
    fun `validateMokafaaOtp omits package_type_id when not provided`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"session-1","status":"reversed"}"""
            )
        )

        repository.validateMokafaaOtp(sessionId = "session-1", otp = "1234")

        val body = mockWebServer.takeRequest().body.readUtf8()
        assertFalse(body.contains("package_type_id"))
    }

    @Test
    fun `initiateMokafaaOtp parses masked phone number when present`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"session_id":"session-1","expires_at":"2026-06-11T10:05:00.000Z","masked_phone_number":"+966 5* *** **89"}
                """.trimIndent()
            )
        )

        val response = repository.initiateMokafaaOtp(MokafaaOtpInitiateRequest.Purpose.ENROLLMENT)

        assertEquals("+966 5* *** **89", response.maskedPhoneNumber)
    }

    @Test
    fun `initiateMokafaaOtp tolerates missing masked phone number`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"session_id":"session-1","expires_at":"2026-06-11T10:05:00.000Z"}"""
            )
        )

        val response = repository.initiateMokafaaOtp(MokafaaOtpInitiateRequest.Purpose.ENROLLMENT)

        assertNull(response.maskedPhoneNumber)
    }

    @Test
    fun `mokafaa error prefers localized message over detail`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"detail":"Payment request failed","message":"الرصيد غير كافٍ"}"""
            )
        )

        try {
            repository.validateMokafaaOtp(sessionId = "session-1", otp = "1234", points = 500)
            fail("Expected LoyaltyApiException")
        } catch (e: LoyaltyApiException) {
            assertEquals("الرصيد غير كافٍ", e.message)
        }
    }

    @Test
    fun `mokafaa 400 surfaces backend message verbatim with status code`() = runTest {
        val backendMessage = "An OTP was already sent. Please wait and try again."
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"message":"$backendMessage"}""")
        )

        try {
            repository.initiateMokafaaOtp(MokafaaOtpInitiateRequest.Purpose.CHECKOUT)
            fail("Expected LoyaltyApiException")
        } catch (e: LoyaltyApiException) {
            assertEquals(400, e.httpCode)
            assertEquals(backendMessage, e.message)
        }
    }

    @Test
    fun `mokafaa 503 carries status code`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("<html>unavailable</html>"))

        try {
            repository.validateMokafaaOtp(sessionId = "session-1", otp = "1234", points = 10)
            fail("Expected LoyaltyApiException")
        } catch (e: LoyaltyApiException) {
            assertEquals(503, e.httpCode)
        }
    }

    @Test
    fun `getMokafaaQuote sends provider and points and omits loyalty_points_amount`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "package_type_id": 456,
                  "pricing": {"order_currency": {"total": "15.00", "points_applied": "20.00"}},
                  "points": {"applied_cents": 2000}
                }
                """.trimIndent()
            )
        )

        val response = repository.getMokafaaQuote(packageTypeId = 456, loyaltyPointsToUse = 200)

        assertEquals("15.00", response.pricing.orderCurrency.total)
        val request = mockWebServer.takeRequest()
        assertEquals("/api/v2/payments/quote/", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""loyalty_provider":"mokafaa""""))
        assertTrue(body.contains(""""loyalty_points_to_use":200"""))
        assertFalse(body.contains("loyalty_points_amount"))
    }
}
