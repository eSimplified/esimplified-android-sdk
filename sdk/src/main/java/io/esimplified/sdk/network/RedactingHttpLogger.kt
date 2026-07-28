package io.esimplified.sdk.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import timber.log.Timber

internal class RedactingHttpLogger : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNanos = System.nanoTime()

        Timber.tag(TAG).d("--> ${request.method} ${request.url}")
        request.headers.forEach { (name, value) ->
            Timber.tag(TAG).d("$name: ${redactHeaderValue(name, value)}")
        }
        request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            val raw = buffer.readUtf8()
            val contentType = body.contentType()?.toString().orEmpty()
            Timber.tag(TAG).d(redactBody(raw, contentType))
        }

        val response: Response
        try {
            response = chain.proceed(request)
        } catch (exception: Exception) {
            Timber.tag(TAG).e(exception, "<-- HTTP FAILED")
            throw exception
        }

        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        Timber.tag(TAG).d("<-- ${response.code} ${response.message} ${request.url} (${durationMs}ms)")
        response.headers.forEach { (name, value) ->
            Timber.tag(TAG).d("$name: ${redactHeaderValue(name, value)}")
        }

        val responseBody = response.body
        if (responseBody != null) {
            val source = responseBody.source()
            source.request(Long.MAX_VALUE)
            val raw = source.buffer.clone().readUtf8()
            val contentType = responseBody.contentType()?.toString().orEmpty()
            Timber.tag(TAG).d(redactBody(raw, contentType))
        }

        return response
    }

    private fun redactHeaderValue(name: String, value: String): String =
        if (SENSITIVE_HEADERS.contains(name.lowercase())) REDACTED else value

    private fun redactBody(raw: String, contentType: String): String {
        if (raw.isEmpty()) return "(empty body)"
        return when {
            contentType.contains("json", ignoreCase = true) -> redactJsonBody(raw)
            contentType.contains("x-www-form-urlencoded", ignoreCase = true) -> redactFormBody(raw)
            else -> raw
        }
    }

    private fun redactJsonBody(raw: String): String {
        return try {
            val element = jsonParser.parseToJsonElement(raw)
            jsonParser.encodeToString(JsonElement.serializer(), redactJsonElement(element))
        } catch (_: Exception) {
            raw
        }
    }

    private fun redactJsonElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            for ((key, value) in element) {
                if (SENSITIVE_BODY_KEYS.contains(key.lowercase()) && value is JsonPrimitive && value.contentOrNullIfNull() != null) {
                    put(key, JsonPrimitive(REDACTED))
                } else {
                    put(key, redactJsonElement(value))
                }
            }
        }
        is JsonArray -> buildJsonArray { element.forEach { add(redactJsonElement(it)) } }
        is JsonPrimitive, JsonNull -> element
    }

    private fun JsonPrimitive.contentOrNullIfNull(): String? =
        if (this is JsonNull) null else content

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

    private companion object {
        const val TAG = "EsimplifiedSdkHttp"
        const val REDACTED = "***REDACTED***"

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
            "client_secret",
            "secret",
            "refresh_token",
            "access_token",
            "token",
            "ephemeral_key",
            "publishable_key",
            "activation_code",
            "qr_code",
            "sm_dp_address",
            "customer_ref",
        )

        val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
