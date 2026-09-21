package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.network.SdkError
import io.esimplified.sdk.repository.impl.CountryRepositoryImpl
import io.esimplified.sdk.repository.impl.NotificationRepositoryImpl
import io.esimplified.sdk.repository.impl.VouchersRepositoryImpl
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class TypedApiErrorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
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

    // region A message the customer should see
    @Test
    fun `a rejected voucher surfaces the server message verbatim`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"detail":"Voucher code is not valid."}""")
        )

        val failure = VouchersRepositoryImpl(apiService).redeemVoucher("NOPE").exceptionOrNull()

        assertTrue("Expected a NetworkError, got $failure", failure is SdkError.NetworkError)
        assertEquals(400, (failure as SdkError.NetworkError).statusCode)
        assertEquals("Voucher code is not valid.", failure.message)
    }

    @Test
    fun `an http failure on a plain read carries the parsed message and status`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(422).setBody("""{"detail":"Notifications are disabled."}""")
        )

        val failure = runCatching {
            NotificationRepositoryImpl(apiService).getSettings()
        }.exceptionOrNull()

        assertTrue("Expected a NetworkError, got $failure", failure is SdkError.NetworkError)
        assertEquals(422, (failure as SdkError.NetworkError).statusCode)
        assertEquals("Notifications are disabled.", failure.message)
    }

    @Test
    fun `an http failure on a cached read carries the parsed message and status`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(503).setBody("""{"detail":"Upstream is down."}""")
        )

        val result = CountryRepositoryImpl(apiService, SdkCache()).getCountriesResult()

        val failure = result.failure
        assertTrue("Expected a NetworkError, got $failure", failure is SdkError.NetworkError)
        assertEquals(503, (failure as SdkError.NetworkError).statusCode)
        assertEquals("Upstream is down.", failure.message)
        assertFalse(failure.isOffline)
    }

    @Test
    fun `a field error is labelled rather than shown raw`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"voucher_code":["This field is required."]}""")
        )

        val failure = VouchersRepositoryImpl(apiService).redeemVoucher("").exceptionOrNull()

        assertEquals("Voucher code: This field is required.", failure?.message)
    }
    // endregion

    // region A message the customer should not see
    @Test
    fun `an http failure with an empty body still carries a message`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody(""))

        val failure = runCatching {
            NotificationRepositoryImpl(apiService).getSettings()
        }.exceptionOrNull()

        assertTrue("Expected a NetworkError, got $failure", failure is SdkError.NetworkError)
        assertEquals(500, (failure as SdkError.NetworkError).statusCode)
        assertNotNull(failure.message)
        assertTrue("Expected a non-empty message", failure.message!!.isNotEmpty())
    }

    @Test
    fun `an http failure with a body that is not json still carries a message`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        val failure = runCatching {
            NotificationRepositoryImpl(apiService).getSettings()
        }.exceptionOrNull()

        assertTrue("Expected a NetworkError, got $failure", failure is SdkError.NetworkError)
        assertTrue("Expected a non-empty message", failure!!.message!!.isNotEmpty())
    }

    @Test
    fun `a response the model cannot decode stays generic`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"settings":"not a list"}""")
        )

        val failure = runCatching {
            NotificationRepositoryImpl(apiService).getSettings()
        }.exceptionOrNull()

        assertTrue("Expected a DecodingError, got $failure", failure is SdkError.DecodingError)
        assertEquals(SdkError.GENERIC_FAILURE_MESSAGE, failure!!.message)
        assertNotNull(failure.cause)
    }

    @Test
    fun `a decoding failure never repeats the serializer complaint`() {
        val cause = IllegalArgumentException("Expected start of the array at path \$.results")

        val error = SdkError.DecodingError(cause)

        assertEquals(SdkError.GENERIC_FAILURE_MESSAGE, error.message)
        assertFalse(error.message!!.contains("path"))
        assertFalse(error.message!!.contains("array"))
    }
    // endregion

    // region No repository throws a bare exception
    @Test
    fun `no repository implementation constructs a bare java Exception`() {
        val offenders = repositoryImplSources()
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> BARE_EXCEPTION.containsMatchIn(line) }
                    .map { (index, line) -> "${file.name}:${index + 1} ${line.trim()}" }
            }

        assertEquals(
            "Repositories must throw a typed SdkError, not a bare Exception",
            emptyList<String>(),
            offenders,
        )
    }

    private fun repositoryImplSources(): List<File> {
        val relative = "src/main/java/io/esimplified/sdk/repository"
        val root = listOf(File(relative), File("sdk/$relative"), File("../$relative"))
            .firstOrNull { it.isDirectory }
        assertNotNull("Could not locate the repository sources from ${File(".").absolutePath}", root)
        val sources = root!!.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Found no repository sources to scan", sources.size > 10)
        return sources
    }

    private companion object {
        val BARE_EXCEPTION = Regex("""(throw|failure\(|Result\.failure\()\s*Exception\(""")
    }
    // endregion
}
