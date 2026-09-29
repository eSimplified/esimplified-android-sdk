package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.VisaRewardsResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.impl.VisaRewardsRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class VisaRewardsRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: VisaRewardsRepositoryImpl

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
        repository = VisaRewardsRepositoryImpl(apiService)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `a redeem onto an existing esim sends the reward type as returned and the iccid`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"redeemed":true}"""))

        repository.activate(token = "tok123", rewardCode = "GLOBAL_ESIM", iccid = "8944000000000000001")

        val request = mockWebServer.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v2/customer/promotions/validate/tok123", request.path)
        assertEquals("reward_type=GLOBAL_ESIM&iccid=8944000000000000001", request.body.readUtf8())
    }

    @Test
    fun `a redeem without an iccid sends only the reward type`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"redeemed":true}"""))

        repository.activate(token = "tok123", rewardCode = "discount")

        assertEquals("reward_type=discount", mockWebServer.takeRequest().body.readUtf8())
    }

    @Test
    fun `the order id comes from the id query item and falls back to the last equals`() {
        assertEquals("abc-123", VisaRewardsResponse(redirectURl = "order/7946?id=abc-123").orderUuid)
        assertEquals("abc-123", VisaRewardsResponse(redirectURl = "order/7946?x=1&id=abc-123&y=2").orderUuid)
        assertEquals("tail-9", VisaRewardsResponse(redirectURl = "order/7946?order=tail-9").orderUuid)
        assertNull(VisaRewardsResponse(redirectURl = "order/7946").orderUuid)
        assertNull(VisaRewardsResponse(redirectURl = "").orderUuid)
        assertNull(VisaRewardsResponse().orderUuid)
    }
}
