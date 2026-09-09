package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.impl.EsimRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class EsimRepositoryImplTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ApiService
    private lateinit var cache: SdkCache

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        cache = SdkCache()

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

    // region Cache keys
    @Test
    fun `the default list key carries showLegacy true and the any primary sentinel`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims()

        assertTrue(cache.store.containsKey("esims_false_legacytrue_primaryany"))
    }

    @Test
    fun `showLegacy false changes the list key`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims(showLegacy = false)

        assertTrue(cache.store.containsKey("esims_false_legacyfalse_primaryany"))
        assertFalse(cache.store.containsKey("esims_false_legacytrue_primaryany"))
    }

    @Test
    fun `isPrimary true changes the list key`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims(isPrimary = true)

        assertTrue(cache.store.containsKey("esims_false_legacytrue_primarytrue"))
    }

    @Test
    fun `isPrimary false changes the list key and is not the any sentinel`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims(isPrimary = false)

        assertTrue(cache.store.containsKey("esims_false_legacytrue_primaryfalse"))
        assertFalse(cache.store.containsKey("esims_false_legacytrue_primaryany"))
    }

    @Test
    fun `an unset isPrimary caches separately from an explicit one`() = runTest {
        enqueueEmptyEsims()
        enqueueEmptyEsims()
        val repo = repo()

        repo.getActiveEsims()
        repo.getActiveEsims(isPrimary = true)

        assertEquals(2, mockWebServer.requestCount)
        assertTrue(cache.store.containsKey("esims_false_legacytrue_primaryany"))
        assertTrue(cache.store.containsKey("esims_false_legacytrue_primarytrue"))
    }

    @Test
    fun `each showLegacy value caches separately`() = runTest {
        enqueueEmptyEsims()
        enqueueEmptyEsims()
        val repo = repo()

        repo.getActiveEsims(showLegacy = true)
        repo.getActiveEsims(showLegacy = false)

        assertEquals(2, mockWebServer.requestCount)
        assertEquals(2, cache.store.size)
    }

    @Test
    fun `repeating the same parameters serves the cached list`() = runTest {
        enqueueEmptyEsims()
        val repo = repo()

        repo.getActiveEsims(showLegacy = false, isPrimary = true)
        repo.getActiveEsims(showLegacy = false, isPrimary = true)

        assertEquals(1, mockWebServer.requestCount)
    }

    @Test
    fun `the archived list keeps its own key per parameter set`() = runTest {
        enqueueEmptyEsims()
        enqueueEmptyEsims()
        val repo = repo()

        repo.getActiveEsims(isPrimary = true)
        repo.getArchivedEsims(isPrimary = true)

        assertTrue(cache.store.containsKey("esims_false_legacytrue_primarytrue"))
        assertTrue(cache.store.containsKey("esims_true_legacytrue_primarytrue"))
    }
    // endregion

    // region Query parameters
    @Test
    fun `the list request sends show_legacy and omits is_primary when unset`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims()

        val path = mockWebServer.takeRequest().path
        assertNotNull(path)
        assertTrue(path!!.contains("show_legacy=true"))
        assertFalse(path.contains("is_primary"))
    }

    @Test
    fun `the list request sends show_legacy false when legacy is suppressed`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims(showLegacy = false)

        assertTrue(mockWebServer.takeRequest().path!!.contains("show_legacy=false"))
    }

    @Test
    fun `the list request sends is_primary when it is set`() = runTest {
        enqueueEmptyEsims()

        repo().getActiveEsims(isPrimary = true)

        assertTrue(mockWebServer.takeRequest().path!!.contains("is_primary=true"))
    }
    // endregion

    // region Set primary
    @Test
    fun `updateEsimPrimaryStatus sends the is_primary field`() = runTest {
        enqueueUpdateAccepted()

        repo().updateEsimPrimaryStatus(iccid = "8931", isPrimary = true)

        val request = mockWebServer.takeRequest()
        assertEquals("PUT", request.method)
        assertTrue(request.path!!.contains("/api/v2/customer/esims/8931/"))
        assertEquals("is_primary=true", request.body.readUtf8())
    }

    @Test
    fun `updateEsimPrimaryStatus can clear the primary flag`() = runTest {
        enqueueUpdateAccepted()

        repo().updateEsimPrimaryStatus(iccid = "8931", isPrimary = false)

        assertEquals("is_primary=false", mockWebServer.takeRequest().body.readUtf8())
    }

    @Test
    fun `updateEsimPrimaryStatus drops every eSIM list and that detail entry`() = runTest {
        cache.set("esims_false_legacytrue_primaryany", listOf("active"))
        cache.set("esims_true_legacyfalse_primarytrue", listOf("archived"))
        cache.set("esim_details_8931", "details")
        cache.set("esim_details_9999", "other")
        cache.set("countries_all", listOf("ZA"))
        enqueueUpdateAccepted()

        repo().updateEsimPrimaryStatus(iccid = "8931", isPrimary = true)

        assertNull(cache.getExpired<List<String>>("esims_false_legacytrue_primaryany"))
        assertNull(cache.getExpired<List<String>>("esims_true_legacyfalse_primarytrue"))
        assertNull(cache.getExpired<String>("esim_details_8931"))
        assertNotNull(cache.getExpired<String>("esim_details_9999"))
        assertNotNull(cache.getExpired<List<String>>("countries_all"))
    }

    @Test
    fun `updateEsim forwards isPrimary alongside the other fields`() = runTest {
        enqueueUpdateAccepted()

        repo().updateEsim(iccid = "8931", name = "Trip", isPrimary = true)

        val body = mockWebServer.takeRequest().body.readUtf8()
        assertTrue(body.contains("esim_name=Trip"))
        assertTrue(body.contains("is_primary=true"))
    }

    @Test
    fun `updateEsim without isPrimary sends no is_primary field`() = runTest {
        enqueueUpdateAccepted()

        repo().updateEsim(iccid = "8931", name = "Trip")

        assertFalse(mockWebServer.takeRequest().body.readUtf8().contains("is_primary"))
    }
    // endregion

    // region Update failures
    @Test
    fun `a rename rejected by the server throws with the parsed message`() = runTest {
        enqueueUpdateFailure(500, """{"message":"That name is already taken"}""")

        val error = runCatching { repo().updateEsim(iccid = "8931", name = "Trip") }.exceptionOrNull()

        assertEquals("That name is already taken", error?.message)
    }

    @Test
    fun `an archive rejected by the server throws with the parsed message`() = runTest {
        enqueueUpdateFailure(500, """{"detail":"eSIM cannot be archived"}""")

        val error =
            runCatching { repo().updateEsim(iccid = "8931", isArchived = true) }.exceptionOrNull()

        assertEquals("eSIM cannot be archived", error?.message)
    }

    @Test
    fun `an auto top up change rejected by the server throws with the parsed message`() = runTest {
        enqueueUpdateFailure(500, """{"auto_top_up":["No payment method on file"]}""")

        val error =
            runCatching { repo().updateEsim(iccid = "8931", isAutoTopUp = true) }.exceptionOrNull()

        assertEquals("Auto top up: No payment method on file", error?.message)
    }

    @Test
    fun `a set primary rejected by the server throws with the parsed message`() = runTest {
        enqueueUpdateFailure(500, """{"message":"Another eSIM is already primary"}""")

        val error = runCatching {
            repo().updateEsimPrimaryStatus(iccid = "8931", isPrimary = true)
        }.exceptionOrNull()

        assertEquals("Another eSIM is already primary", error?.message)
    }

    @Test
    fun `a failure with an unparseable body throws the fallback message`() = runTest {
        enqueueUpdateFailure(500, "")

        val error = runCatching { repo().updateEsim(iccid = "8931", name = "Trip") }.exceptionOrNull()

        assertEquals("Unknown error", error?.message)
    }

    @Test
    fun `a 200 whose message is not the success message throws that message`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"message":"eSIM was not updated"}""")
        )

        val error = runCatching { repo().updateEsim(iccid = "8931", name = "Trip") }.exceptionOrNull()

        assertEquals("eSIM was not updated", error?.message)
    }

    @Test
    fun `a 200 with no body throws because the update was not confirmed`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val error = runCatching { repo().updateEsim(iccid = "8931", name = "Trip") }.exceptionOrNull()

        assertEquals("The update did not succeed", error?.message)
    }

    @Test
    fun `a confirmed update throws nothing and still clears the cache`() = runTest {
        cache.set("esims_false_legacytrue_primaryany", listOf("active"))
        cache.set("esim_details_8931", "details")
        enqueueUpdateAccepted()

        val error = runCatching { repo().updateEsim(iccid = "8931", name = "Trip") }.exceptionOrNull()

        assertNull(error)
        assertNull(cache.getExpired<List<String>>("esims_false_legacytrue_primaryany"))
        assertNull(cache.getExpired<String>("esim_details_8931"))
    }
    // endregion

    private fun repo() = EsimRepositoryImpl(apiService, cache)

    private fun enqueueEmptyEsims() = enqueueJson("""{"count":0,"results":[]}""")

    private fun enqueueUpdateFailure(code: Int, body: String) = mockWebServer.enqueue(
        MockResponse().setResponseCode(code).setBody(body)
    )

    private fun enqueueUpdateAccepted() = mockWebServer.enqueue(
        MockResponse().setResponseCode(200).setBody("""{"message":"eSIM updated successfully"}""")
    )

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }
}
