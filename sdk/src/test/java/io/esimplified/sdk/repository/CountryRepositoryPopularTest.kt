package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.network.SdkError
import io.esimplified.sdk.repository.impl.CountryRepositoryImpl
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
import kotlin.time.Duration

class CountryRepositoryPopularTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: CountryRepositoryImpl

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
        repository = CountryRepositoryImpl(apiService, SdkCache())
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    // region Request
    @Test
    fun `popular countries ask for the Popular region with a limit of 1000`() = runTest {
        enqueuePopular("OM", "JP")

        repository.getPopularCountries()

        val path = mockWebServer.takeRequest().path.orEmpty()
        assertTrue(path, path.startsWith("/api/v2/countries/"))
        assertTrue(path, path.contains("region=Popular"))
        assertTrue(path, path.contains("limit=1000"))
    }
    // endregion

    // region Order and caching
    @Test
    fun `popular countries keep the server's order`() = runTest {
        enqueuePopular("OM", "JP", "ZA")

        assertEquals(listOf("OM", "JP", "ZA"), repository.getPopularCountries().map { it.code })
    }

    @Test
    fun `a second read within the ttl is served from cache`() = runTest {
        enqueuePopular("OM")

        repository.getPopularCountries()
        repository.getPopularCountries()

        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `a failed refresh serves the expired list as stale`() = runTest {
        enqueuePopular("OM")
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"Down."}"""))

        repository.getPopularCountries(cacheTTL = Duration.ZERO)
        val result = repository.getPopularCountriesResult(cacheTTL = Duration.ZERO)

        assertEquals(listOf("OM"), result.value.map { it.code })
        assertTrue(result.isStale)
        assertTrue(result.failure is SdkError.NetworkError)
    }

    @Test
    fun `invalidating the cache drops the popular list`() = runTest {
        enqueuePopular("OM")
        enqueuePopular("JP")

        repository.getPopularCountries()
        repository.invalidateCache()

        assertEquals(listOf("JP"), repository.getPopularCountries().map { it.code })
    }
    // endregion

    private fun enqueuePopular(vararg codes: String) {
        val results = codes.joinToString(",") { code ->
            """{"country_name":"Country $code","country_code":"$code","country_flag":"flag",""" +
                """"country_flag_css":"css","country_name_slug":"slug-$code"}"""
        }
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"count":${codes.size},"results":[$results]}""")
        )
    }
}
