package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json


@Serializable
data class CountryCode(
    @SerialName("id") var id: String,
    @SerialName("name") var name: String,
    @SerialName("flag") var emoji: String,
    @SerialName("code") var isoCode: String,
    @SerialName("dial_code") var dialCode: String,
    @SerialName("pattern") var pattern: String,
    @SerialName("limit") var limit: Int
) {
    companion object {
        fun getAllFrom(data: String): List<CountryCode> {
            return runCatching {
                val json = Json { ignoreUnknownKeys = true }
                return json.decodeFromString<List<CountryCode>>(data)
            }.getOrDefault(emptyList())
        }
    }
}
