package io.esimplified.sdk.network

import io.esimplified.sdk.SdkLog
import io.esimplified.sdk.SdkLogLevel
import io.esimplified.sdk.SdkLogger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RedactingHttpLoggerTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var logger: RecordingLogger

    @Before
    fun setup() {
        logger = RecordingLogger()
        SdkLog.delegate = logger
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client = OkHttpClient.Builder()
            .addInterceptor(RedactingHttpLogger())
            .build()
    }

    @After
    fun teardown() {
        SdkLog.resetForTesting()
        mockWebServer.shutdown()
    }

    // region Response bodies
    @Test
    fun `every sensitive key in a json response is redacted`() {
        val secrets = mapOf(
            "access_token" to "at-secret",
            "refresh_token" to "rt-secret",
            "token" to "t-secret",
            "id_token" to "id-secret",
            "password" to "pw-secret",
            "current_password" to "cpw-secret",
            "new_password" to "npw-secret",
            "old_password" to "opw-secret",
            "password_reset_encoded" to "pre-secret",
            "client_secret" to "cs-secret",
            "secret" to "s-secret",
            "otp" to "123456",
            "session_id" to "sid-secret",
            "ephemeral_key" to "ek-secret",
            "publishable_key" to "pk-secret",
            "activation_code" to "ac-secret",
            "qr_code_image_base64" to "qrb64-secret",
            "image_base64" to "b64-secret",
            "image_url" to "https://example.test/qr.png",
            "uri" to "https://pay.test/intent-secret",
            "sm_dp_address" to "smdp.test",
            "matching_id" to "mid-secret",
            "customer_ref" to "cref-secret",
            "username" to "person@example.test",
            "email" to "person@example.test",
            "new_email" to "new-person@example.test",
            "phone_number" to "+441234567890",
            "first_name" to "Ada",
            "last_name" to "Lovelace",
            "full_name" to "Ada Lovelace",
            "customer_id" to "cus-secret",
            "mokafaa_cic_no" to "cic-secret",
            "provider_account_id" to "paid-secret",
            "referral_code" to "ref-secret",
            "referred_by" to "refby-secret",
            "external_reference" to "ext-secret",
            "iccid" to "8944000000000000000",
            "eid" to "eid-secret",
            "imsi" to "imsi-secret",
            "msisdn" to "msisdn-secret",
            "order_uuid" to "order-secret",
            "transaction_id" to "txn-secret",
            "voucher_code" to "voucher-secret",
            "promo_code" to "promo-secret",
            "discount_code" to "discount-secret",
            "coupon_id" to "coupon-secret",
        )
        val body = secrets.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            """"$key":"$value""""
        }

        get(body = body)

        secrets.forEach { (key, value) ->
            assertFalse("$key leaked: ${logger.rendered()}", logger.rendered().contains(value))
        }
    }

    @Test
    fun `a sensitive key holding an object is redacted whole`() {
        get(
            body = """
                {"qr_code":{"image_base64":"QR-PAYLOAD","image_url":"https://example.test/qr.png"},
                 "user":{"customer_id":"cus-1","email":"person@example.test"}}
            """.trimIndent()
        )

        assertFalse("The QR payload leaked: ${logger.rendered()}", logger.rendered().contains("QR-PAYLOAD"))
        assertFalse(logger.rendered().contains("person@example.test"))
        assertFalse(logger.rendered().contains("cus-1"))
    }

    @Test
    fun `a sensitive key holding an array is redacted whole`() {
        get(body = """{"iccid":["8944000000000000001","8944000000000000002"]}""")

        assertFalse(logger.rendered().contains("8944000000000000001"))
        assertFalse(logger.rendered().contains("8944000000000000002"))
    }

    @Test
    fun `a sensitive key nested inside a plain object is still redacted`() {
        get(body = """{"data":{"items":[{"esim":{"iccid":"8944000000000000003"}}]}}""")

        assertFalse(logger.rendered().contains("8944000000000000003"))
    }

    @Test
    fun `a null sensitive key is left alone`() {
        get(body = """{"refresh_token":null,"status":"ok"}""")

        assertTrue(logger.rendered().contains("\"refresh_token\":null"))
        assertTrue(logger.rendered().contains("ok"))
    }
    // endregion

    // region URLs
    @Test
    fun `query parameter values never reach the log`() {
        get(path = "/api/v2/search/?search_term=paris&token=access-token-secret")

        assertFalse("A query value leaked: ${logger.rendered()}", logger.rendered().contains("paris"))
        assertFalse(logger.rendered().contains("access-token-secret"))
        assertTrue(logger.rendered().contains("search_term=***REDACTED***"))
        assertTrue(logger.rendered().contains("token=***REDACTED***"))
    }

    @Test
    fun `identifying path segments are redacted`() {
        get(path = "/api/v2/customer/orders/9f8e7d6c-1234-5678-9abc-def012345678/")

        assertFalse(
            "An order uuid leaked: ${logger.rendered()}",
            logger.rendered().contains("9f8e7d6c-1234-5678-9abc-def012345678")
        )
        assertTrue(logger.rendered().contains("/api/v2/customer/orders/"))
    }
    // endregion

    // region Request bodies
    @Test
    fun `a form encoded login redacts the username and the password`() {
        post(
            body = "grant_type=password&username=person%40example.test&password=hunter2",
            contentType = "application/x-www-form-urlencoded",
        )

        assertFalse("The username leaked: ${logger.rendered()}", logger.rendered().contains("person%40example.test"))
        assertFalse(logger.rendered().contains("hunter2"))
        assertTrue(logger.rendered().contains("username=***REDACTED***"))
        assertTrue(logger.rendered().contains("password=***REDACTED***"))
        assertTrue(logger.rendered().contains("grant_type=password"))
    }

    @Test
    fun `a json request body is redacted too`() {
        post(body = """{"email":"person@example.test","marketing_consent":true}""", contentType = "application/json")

        assertFalse(logger.rendered().contains("person@example.test"))
        assertTrue(logger.rendered().contains("marketing_consent"))
    }
    // endregion

    // region Bodies that are not logged at all
    @Test
    fun `a pdf response is summarised rather than dumped`() {
        val pdf = "%PDF-1.7 binary-invoice-payload"
        get(body = pdf, contentType = "application/pdf")

        assertFalse("The PDF was dumped: ${logger.rendered()}", logger.rendered().contains("binary-invoice-payload"))
        assertTrue(logger.rendered().contains("application/pdf"))
        assertTrue(logger.rendered().contains("not logged"))
    }

    @Test
    fun `an oversized json response is summarised rather than dumped`() {
        val filler = "x".repeat(64 * 1024)
        get(body = """{"note":"$filler"}""")

        assertFalse("A huge body was dumped: ${logger.rendered().length}", logger.rendered().contains(filler))
        assertTrue(logger.rendered().contains("not logged"))
    }

    @Test
    fun `a body that claims to be json but is not is never dumped`() {
        get(body = "<html><body>tokens: access-token-secret</body></html>")

        assertFalse(logger.rendered().contains("access-token-secret"))
        assertTrue(logger.rendered().contains("unparseable"))
    }
    // endregion

    private fun get(
        path: String = "/api/v2/test/",
        body: String = "{}",
        contentType: String = "application/json",
    ) {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("content-type", contentType)
                .setBody(body)
        )
        client.newCall(Request.Builder().url(mockWebServer.url(path)).build()).execute().close()
    }

    private fun post(body: String, contentType: String) {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        client.newCall(
            Request.Builder()
                .url(mockWebServer.url("/api/v2/test/"))
                .post(body.toRequestBody(contentType.toMediaType()))
                .build()
        ).execute().close()
    }

    private class RecordingLogger : SdkLogger {
        private val lines = mutableListOf<String>()

        override fun log(level: SdkLogLevel, message: String, throwable: Throwable?) {
            synchronized(lines) { lines += "$message ${throwable?.toString().orEmpty()}" }
        }

        fun rendered(): String = synchronized(lines) { lines.joinToString("\n") }
    }
}
