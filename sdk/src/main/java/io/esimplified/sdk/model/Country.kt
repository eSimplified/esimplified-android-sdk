package io.esimplified.sdk.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class Country(
    @SerialName("country_name")
    val name: String = "",
    @SerialName("country_code")
    val code: String = "",
    @SerialName("country_flag")
    val flag: String = "",
    @SerialName("country_flag_css")
    val flagCss: String = "",
    @SerialName("country_name_slug")
    val slug: String = "",
    @SerialName("supported_countries")
    val destinations: List<SupportedCountry> = listOf(),
    @SerialName("is_region")
    val isRegion: Boolean = false,
    @SerialName("from_price")
    @Serializable(with = LenientPriceSerializer::class)
    val fromPrice: Double? = null,
    @SerialName("currency")
    val currency: String? = null,
    @SerialName("currency_obj")
    val currencyObject: CurrencyObject? = null,
) {

    val isGlobal: Boolean = code == GLOBAL_CODE

    companion object {
        private const val GLOBAL_CODE = "2A"
    }
}

// region Lenient price
internal object LenientPriceSerializer : KSerializer<Double?> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(LENIENT_PRICE_SERIAL_NAME, PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = json.decodeJsonElement() as? JsonPrimitive ?: return null
        return primitive.content.toDoubleOrNull()
    }
}

internal const val LENIENT_PRICE_SERIAL_NAME = "io.esimplified.sdk.model.LenientPrice"
// endregion

// region Tolerant country
internal object TolerantCountrySerializer : KSerializer<Country> {

    override val descriptor: SerialDescriptor = Country.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Country) =
        Country.serializer().serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Country {
        val json = decoder as? JsonDecoder ?: return Country.serializer().deserialize(decoder)
        val element = json.decodeJsonElement()
        if (element is JsonObject) {
            return json.json.decodeFromJsonElement(Country.serializer(), element)
        }
        val name = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (name == null) Country() else Country(name = name)
    }
}
// endregion
