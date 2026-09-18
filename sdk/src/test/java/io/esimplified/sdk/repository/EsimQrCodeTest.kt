package io.esimplified.sdk.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.model.AssignedEsim
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create

class EsimQrCodeTest {

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

    // region The new fields decode
    @Test
    fun `an eSIM decodes the address, matching ID and QR code the API now returns`() = runTest {
        enqueueEsimWithQr()

        val esim = repo().getActiveEsims(includeBase64QrCode = true).first()

        assertEquals("test.esim.com", esim.smDpAddress)
        assertEquals("25011473", esim.activationCode)
        assertEquals("aVZCT1J3MEtHZ28=", esim.qrCodeImageBase64)
        assertEquals("TEST", esim.esimProvider)
    }

    @Test
    fun `an eSIM without the fields decodes them as null`() = runTest {
        enqueueJson(ESIM_WITHOUT_QR)

        val esim = repo().getActiveEsims().first()

        assertEquals(null, esim.smDpAddress)
        assertEquals(null, esim.activationCode)
        assertEquals(null, esim.qrCodeImageBase64)
        assertEquals(null, esim.esimProvider)
    }

    @Test
    fun `the eSIM's matching ID is its own, not the LPA string on the profile`() = runTest {
        enqueueEsimWithQr()

        val esim = repo().getActiveEsims(includeBase64QrCode = true).first()

        assertEquals("LPA:1\$test.esim.com\$TN226021414E0101F", esim.profile?.activationCode)
        assertNotEquals(esim.profile?.activationCode, esim.activationCode)
    }
    // endregion

    // region canInstallDirectly
    @Test
    fun `an eSIM carrying both halves reports that it can install on its own`() = runTest {
        enqueueEsimWithQr()

        assertTrue(repo().getActiveEsims(includeBase64QrCode = true).first().canInstallDirectly)
    }

    @Test
    fun `an eSIM missing either half reports that it cannot`() {
        assertFalse(baseEsim().canInstallDirectly)
        assertFalse(baseEsim().copy(smDpAddress = "test.esim.com").canInstallDirectly)
        assertFalse(baseEsim().copy(activationCode = "25011473").canInstallDirectly)
        assertFalse(baseEsim().copy(smDpAddress = "", activationCode = "").canInstallDirectly)
        assertTrue(
            baseEsim().copy(smDpAddress = "test.esim.com", activationCode = "25011473")
                .canInstallDirectly
        )
    }
    // endregion

    // region The request asks for it
    @Test
    fun `asking for the QR sends include_base64_qr_code on the list read`() = runTest {
        enqueueEsimWithQr()

        repo().getActiveEsims(isPrimary = true, includeBase64QrCode = true)

        val path = mockWebServer.takeRequest().path!!
        assertTrue(path.contains("include_base64_qr_code=true"))
        assertTrue(path.contains("is_primary=true"))
    }

    @Test
    fun `not asking for it leaves the parameter off the list read entirely`() = runTest {
        enqueueEsimWithQr()

        repo().getActiveEsims()

        assertFalse(mockWebServer.takeRequest().path!!.contains("include_base64_qr_code"))
    }

    @Test
    fun `asking for the QR sends the flag on the details read too`() = runTest {
        enqueueJson(ESIM_DETAILS_WITH_QR)

        val esim = repo().getEsimByIccid(iccid = "250700000031473", includeBase64QrCode = true)

        assertTrue(mockWebServer.takeRequest().path!!.contains("include_base64_qr_code=true"))
        assertEquals("aVZCT1J3MEtHZ28=", esim.qrCodeImageBase64)
    }

    @Test
    fun `not asking for it leaves the parameter off the details read entirely`() = runTest {
        enqueueJson(ESIM_DETAILS_WITH_QR)

        repo().getEsimByIccid(iccid = "250700000031473")

        assertFalse(mockWebServer.takeRequest().path!!.contains("include_base64_qr_code"))
    }
    // endregion

    // region A QR-less response is never served to a QR request
    @Test
    fun `a list read without the QR does not satisfy a later read that wants one`() = runTest {
        enqueueEsimWithQr()
        enqueueEsimWithQr()
        val repo = repo()

        repo.getActiveEsims()
        repo.getActiveEsims(includeBase64QrCode = true)

        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `a details read without the QR does not satisfy a later read that wants one`() = runTest {
        enqueueJson(ESIM_DETAILS_WITH_QR)
        enqueueJson(ESIM_DETAILS_WITH_QR)
        val repo = repo()

        repo.getEsimByIccid(iccid = "250700000031473")
        repo.getEsimByIccid(iccid = "250700000031473", includeBase64QrCode = true)

        assertEquals(2, mockWebServer.requestCount)
    }

    @Test
    fun `the QR flag is part of the list cache key`() = runTest {
        enqueueEsimWithQr()
        enqueueEsimWithQr()
        val repo = repo()

        repo.getActiveEsims()
        repo.getActiveEsims(includeBase64QrCode = true)

        assertTrue(cache.store.containsKey("esims_false_legacyunset_primaryany_qrfalse"))
        assertTrue(cache.store.containsKey("esims_false_legacyunset_primaryany_qrtrue"))
    }

    @Test
    fun `the QR flag is part of the details cache key`() = runTest {
        enqueueJson(ESIM_DETAILS_WITH_QR)
        enqueueJson(ESIM_DETAILS_WITH_QR)
        val repo = repo()

        repo.getEsimByIccid(iccid = "250700000031473")
        repo.getEsimByIccid(iccid = "250700000031473", includeBase64QrCode = true)

        assertTrue(cache.store.containsKey("esim_details_250700000031473_qrfalse"))
        assertTrue(cache.store.containsKey("esim_details_250700000031473_qrtrue"))
    }

    @Test
    fun `a QR read is still served from its own cache entry on a repeat`() = runTest {
        enqueueEsimWithQr()
        val repo = repo()

        repo.getActiveEsims(includeBase64QrCode = true)
        val second = repo.getActiveEsims(includeBase64QrCode = true)

        assertEquals(1, mockWebServer.requestCount)
        assertEquals("aVZCT1J3MEtHZ28=", second.first().qrCodeImageBase64)
    }

    @Test
    fun `an update drops both the QR and the QR-less detail entries`() = runTest {
        cache.set("esim_details_8931_qrfalse", "plain")
        cache.set("esim_details_8931_qrtrue", "with qr")
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""{"message":"eSIM updated successfully"}""")
        )

        repo().updateEsim(iccid = "8931", name = "Trip")

        assertFalse(cache.store.containsKey("esim_details_8931_qrfalse"))
        assertFalse(cache.store.containsKey("esim_details_8931_qrtrue"))
    }
    // endregion

    private fun repo() = EsimRepositoryImpl(apiService, cache)

    private fun baseEsim() = AssignedEsim(
        iccid = "1",
        name = null,
        assignedDate = "",
        isArchived = false,
        isAutoTopUp = false,
    )

    private fun enqueueEsimWithQr() = enqueueJson(ESIM_LIST_WITH_QR)

    private fun enqueueJson(body: String) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private companion object {
        const val ESIM_LIST_WITH_QR = """
        {"count":1,"next":null,"previous":null,"results":[
          {"iccid":"250700000031473","order_uuid":"51cb3890","android_sha":false,"archived":false,
           "order_number":"8011","assigned_date":"2026-08-20T12:44:23.339874Z",
           "data_usage_remaining_bytes":-1,"data_usage_remaining_gigabytes":-1,
           "esim_name":"Kieran's eSIM","auto_top_up":false,"is_universal":true,"is_primary":true,
           "esim_provider":"TEST",
           "sm_dp_address":"test.esim.com","activation_code":"25011473",
           "qr_code_image_base64":"aVZCT1J3MEtHZ28=",
           "profile":{"state":"ERROR","last_operation_date":1788989642,
                      "activation_code":"LPA:1${'$'}test.esim.com${'$'}TN226021414E0101F",
                      "reuse_remaining_count":3,"reuse_enabled":true,"cc_required":false,
                      "release_date":1703796970,"state_message":"error"}}
        ]}
        """

        const val ESIM_WITHOUT_QR = """
        {"count":1,"next":null,"previous":null,"results":[
          {"iccid":"250700000031473","android_sha":false,"archived":false,
           "assigned_date":"2026-08-20T12:44:23.339874Z",
           "data_usage_remaining_bytes":-1,"data_usage_remaining_gigabytes":-1,
           "esim_name":"Kieran's eSIM","auto_top_up":false}
        ]}
        """

        const val ESIM_DETAILS_WITH_QR = """
        {"iccid":"250700000031473","android_sha":false,"archived":false,"assigned_date":"",
         "data_usage_remaining_bytes":-1,"data_usage_remaining_gigabytes":-1,
         "auto_top_up":false,"is_primary":true,"esim_name":null,
         "sm_dp_address":"test.esim.com","activation_code":"25011473",
         "qr_code_image_base64":"aVZCT1J3MEtHZ28="}
        """
    }
}
