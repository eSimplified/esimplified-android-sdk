package io.esimplified.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// region Content Document
@Serializable
data class ContentDocument(
    val language: String = "",
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val updatedAt: String? = null,
    val blocks: List<ContentBlock> = emptyList(),
    val children: List<ContentNode> = emptyList(),
)
// endregion

// region Content Node
@Serializable
data class ContentNode(
    val id: String? = null,
    val title: String? = null,
    val description: String? = null,
    val updatedAt: String? = null,
    val blocks: List<ContentBlock> = emptyList(),
    val children: List<ContentNode> = emptyList(),
)
// endregion

// region Content Block
@Serializable(with = ContentBlockSerializer::class)
sealed interface ContentBlock {

    @Serializable
    data class Heading(val text: String = "") : ContentBlock

    @Serializable
    data class Paragraph(val text: String = "") : ContentBlock

    @Serializable
    data class ListBlock(val list: ContentList = ContentList()) : ContentBlock

    data object Unknown : ContentBlock
}

object ContentBlockSerializer : KSerializer<ContentBlock> {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ContentBlock")

    override fun serialize(encoder: Encoder, value: ContentBlock) {
        val output = encoder as JsonEncoder
        output.encodeJsonElement(encode(value))
    }

    override fun deserialize(decoder: Decoder): ContentBlock {
        val input = decoder as? JsonDecoder ?: return ContentBlock.Unknown
        val element = input.decodeJsonElement() as? JsonObject ?: return ContentBlock.Unknown
        return decode(element)
    }

    private fun encode(value: ContentBlock): JsonElement = when (value) {
        is ContentBlock.Heading -> buildJsonObject {
            put("type", "heading")
            put("text", value.text)
        }
        is ContentBlock.Paragraph -> buildJsonObject {
            put("type", "paragraph")
            put("text", value.text)
        }
        is ContentBlock.ListBlock -> buildJsonObject {
            put("type", "list")
            json.encodeToJsonElement(ContentList.serializer(), value.list).let { list ->
                (list as JsonObject).forEach { (key, element) -> put(key, element) }
            }
        }
        ContentBlock.Unknown -> buildJsonObject { put("type", "unknown") }
    }

    private fun decode(element: JsonObject): ContentBlock = when (element.string("type")) {
        "heading" -> ContentBlock.Heading(element.string("text").orEmpty())
        "paragraph" -> ContentBlock.Paragraph(element.string("text").orEmpty())
        "list" -> ContentBlock.ListBlock(decodeList(element))
        else -> ContentBlock.Unknown
    }

    private fun decodeList(element: JsonObject): ContentList =
        runCatching { json.decodeFromJsonElement(ContentList.serializer(), element) }
            .getOrDefault(ContentList())

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
// endregion

// region Content List
@Serializable
data class ContentList(
    val ordered: Boolean = false,
    val marker: ContentListMarker = ContentListMarker.BULLET,
    val items: List<ContentListItem> = emptyList(),
)
// endregion

// region Content List Marker
@Serializable(with = ContentListMarkerSerializer::class)
enum class ContentListMarker {
    @SerialName("decimal")
    DECIMAL,

    @SerialName("alpha")
    ALPHA,

    @SerialName("bullet")
    BULLET,
}

object ContentListMarkerSerializer : KSerializer<ContentListMarker> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ContentListMarker", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ContentListMarker) {
        val string = when (value) {
            ContentListMarker.DECIMAL -> "decimal"
            ContentListMarker.ALPHA -> "alpha"
            ContentListMarker.BULLET -> "bullet"
        }
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): ContentListMarker {
        return when (decoder.decodeString()) {
            "decimal" -> ContentListMarker.DECIMAL
            "alpha" -> ContentListMarker.ALPHA
            else -> ContentListMarker.BULLET
        }
    }
}
// endregion

// region Content List Item
@Serializable
data class ContentListItem(
    val text: String = "",
    val items: List<ContentListItem> = emptyList(),
    val ordered: Boolean? = null,
    val marker: ContentListMarker? = null,
)
// endregion
