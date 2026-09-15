package io.esimplified.sdk.network

import io.esimplified.sdk.model.ApiErrorResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException

internal object ApiErrorMessage {

    const val FALLBACK = "Unknown error"

    private val unlabelledKeys = setOf("non_field_errors", "errors", "error", "detail", "message")

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Parsing
    fun parse(body: String?): String = parseOrNull(body) ?: FALLBACK

    fun parseOrNull(error: HttpException): String? =
        parseOrNull(runCatching { error.response()?.errorBody()?.string() }.getOrNull())

    fun parseOrNull(body: String?): String? {
        if (body == null) return null

        decodeApiErrorResponse(body)?.let { return it }

        val element = runCatching { json.parseToJsonElement(body) }.getOrNull()
        if (element != null && element !is JsonPrimitive) {
            return flatten(element, null).joinToString("\n").ifEmpty { null }
        }

        return body.trim().ifEmpty { null }
    }
    // endregion

    // region Internals
    private fun decodeApiErrorResponse(body: String): String? = runCatching {
        val decoded = json.decodeFromString<ApiErrorResponse>(body)
        decoded.message ?: decoded.detail ?: decoded.error
    }.getOrNull()

    private fun flatten(element: JsonElement, key: String?): List<String> = when (element) {
        is JsonPrimitive -> flattenPrimitive(element, key)
        is JsonArray -> element.flatMap { flatten(it, key) }
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .flatMap { flatten(it.value, it.key) }
    }

    private fun flattenPrimitive(primitive: JsonPrimitive, key: String?): List<String> {
        if (!primitive.isString) return emptyList()
        val value = primitive.content
        if (key == null || key in unlabelledKeys) return listOf(value)
        return listOf("${humanize(key)}: $value")
    }

    private fun humanize(key: String): String =
        key.replace("_", " ").replaceFirstChar { it.uppercase() }
    // endregion
}
