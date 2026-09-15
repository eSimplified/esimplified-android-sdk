package io.esimplified.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

@Serializable
data class SupportedCountry(
    @SerialName("country_name")
    val name: String = "",
    @SerialName("country_code")
    val code: String = "",
)

// region Tolerant supported countries
internal object TolerantSupportedCountriesSerializer : KSerializer<List<SupportedCountry>> {

    private val delegate = ListSerializer(SupportedCountry.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<SupportedCountry>) =
        delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<SupportedCountry> {
        val json = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val element = json.decodeJsonElement()
        if (element !is JsonArray) return emptyList()
        return runCatching { json.json.decodeFromJsonElement(delegate, element) }
            .getOrDefault(emptyList())
    }
}
// endregion
