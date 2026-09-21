package io.esimplified.sdk.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.Buffer
import io.esimplified.sdk.SdkLog
import io.esimplified.sdk.redactedPath

internal class RedactingHttpLogger : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNanos = System.nanoTime()

        SdkLog.d("$TAG --> ${request.method} ${redactedUrl(request.url)}")
        request.headers.forEach { (name, value) ->
            SdkLog.d("$TAG $name: ${redactHeaderValue(name, value)}")
        }
        request.body?.let { logRequestBody(it) }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (exception: Exception) {
            SdkLog.e("$TAG <-- HTTP FAILED", exception)
            throw exception
        }

        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        SdkLog.d("$TAG <-- ${response.code} ${response.message} ${redactedUrl(request.url)} (${durationMs}ms)")
        response.headers.forEach { (name, value) ->
            SdkLog.d("$TAG $name: ${redactHeaderValue(name, value)}")
        }

        logResponseBody(response)

        return response
    }

    // region URLs
    private fun redactedUrl(url: HttpUrl): String {
        val path = "${url.host}${url.encodedPath.redactedPath()}"
        val names = url.queryParameterNames
        if (names.isEmpty()) return path
        return names.joinToString(separator = "&", prefix = "$path?") { "$it=$REDACTED" }
    }
    // endregion

    // region Bodies
    private fun logRequestBody(body: RequestBody) {
        val contentType = body.contentType()?.toString().orEmpty()
        if (!isRedactable(contentType)) {
            SdkLog.d("$TAG ${bodySummary(contentType, body.contentLength())}")
            return
        }
        if (body.contentLength() > MAX_LOGGED_BODY_BYTES) {
            SdkLog.d("$TAG ${bodySummary(contentType, body.contentLength())}")
            return
        }
        val buffer = Buffer()
        body.writeTo(buffer)
        if (buffer.size > MAX_LOGGED_BODY_BYTES) {
            SdkLog.d("$TAG ${bodySummary(contentType, buffer.size)}")
            return
        }
        SdkLog.d("$TAG ${redactBody(buffer.readUtf8(), contentType)}")
    }

    private fun logResponseBody(response: Response) {
        val body = response.body ?: return
        val contentType = body.contentType()?.toString().orEmpty()
        if (!isRedactable(contentType)) {
            SdkLog.d("$TAG ${bodySummary(contentType, body.contentLength())}")
            return
        }
        val source = body.source()
        source.request(MAX_LOGGED_BODY_BYTES + 1)
        val buffered = source.buffer.size
        if (buffered > MAX_LOGGED_BODY_BYTES) {
            SdkLog.d("$TAG ${bodySummary(contentType, body.contentLength().takeIf { it >= 0 } ?: buffered)}")
            return
        }
        SdkLog.d("$TAG ${redactBody(source.buffer.clone().readUtf8(), contentType)}")
    }

    private fun isRedactable(contentType: String): Boolean =
        contentType.contains("json", ignoreCase = true) ||
            contentType.contains("x-www-form-urlencoded", ignoreCase = true)

    private fun bodySummary(contentType: String, byteCount: Long): String {
        val type = contentType.ifEmpty { "unknown content type" }
        val size = if (byteCount >= 0) "$byteCount bytes" else "unknown length"
        return "($type body, $size — not logged)"
    }

    private fun redactBody(raw: String, contentType: String): String {
        if (raw.isEmpty()) return "(empty body)"
        return when {
            contentType.contains("json", ignoreCase = true) -> redactJsonBody(raw, contentType)
            contentType.contains("x-www-form-urlencoded", ignoreCase = true) -> redactFormBody(raw)
            else -> bodySummary(contentType, raw.length.toLong())
        }
    }

    private fun redactJsonBody(raw: String, contentType: String): String {
        return try {
            val element = jsonParser.parseToJsonElement(raw)
            jsonParser.encodeToString(JsonElement.serializer(), redactJsonElement(element))
        } catch (_: Exception) {
            "(unparseable ${contentType.ifEmpty { "unknown content type" }} body," +
                " ${raw.length} chars — not logged)"
        }
    }

    private fun redactJsonElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            for ((key, value) in element) {
                if (SENSITIVE_BODY_KEYS.contains(key.lowercase()) && value !is JsonNull) {
                    put(key, JsonPrimitive(REDACTED))
                } else {
                    put(key, redactJsonElement(value))
                }
            }
        }
        is JsonArray -> buildJsonArray { element.forEach { add(redactJsonElement(it)) } }
        is JsonPrimitive, JsonNull -> element
    }

    private fun redactFormBody(raw: String): String {
        val pairs = raw.split('&').map { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2) return@map pair
            val key = parts[0]
            val value = parts[1]
            if (SENSITIVE_BODY_KEYS.contains(key.lowercase())) "$key=$REDACTED" else "$key=$value"
        }
        return pairs.joinToString("&")
    }
    // endregion

    private fun redactHeaderValue(name: String, value: String): String =
        if (SENSITIVE_HEADERS.contains(name.lowercase())) REDACTED else value

    private companion object {
        const val TAG = "EsimplifiedSdkHttp"
        const val REDACTED = "***REDACTED***"
        const val MAX_LOGGED_BODY_BYTES = 32L * 1024

        val SENSITIVE_HEADERS = setOf(
            "authorization",
            "x-auth-validation",
            "x-firebase-appcheck",
            "cookie",
            "set-cookie",
        )

        val SENSITIVE_BODY_KEYS = setOf(
            "password",
            "current_password",
            "new_password",
            "old_password",
            "password_reset_encoded",
            "client_secret",
            "secret",
            "refresh_token",
            "access_token",
            "token",
            "otp",
            "session_id",
            "ephemeral_key",
            "publishable_key",
            "activation_code",
            "qr_code",
            "qr_code_image_base64",
            "image_base64",
            "image_url",
            "uri",
            "sm_dp_address",
            "matching_id",
            "customer_ref",
            "id_token",
            "user",
            "username",
            "email",
            "new_email",
            "phone_number",
            "first_name",
            "last_name",
            "full_name",
            "customer_id",
            "mokafaa_cic_no",
            "provider_account_id",
            "referral_code",
            "referred_by",
            "external_reference",
            "iccid",
            "eid",
            "imsi",
            "msisdn",
            "order_uuid",
            "transaction_id",
            "voucher_code",
            "promo_code",
            "discount_code",
            "coupon_id",
        )

        val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
