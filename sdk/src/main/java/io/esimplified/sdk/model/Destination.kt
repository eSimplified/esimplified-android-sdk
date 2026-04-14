package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Destination(
    @SerialName("country_code") val code: String? = null,
    @SerialName("country_name") val name: String? = null,
    @SerialName("country_slug") val slug: String? = null,
    val region: String? = null,
) {
    val key
        get() = listOf(code, slug).filter { !it.isNullOrBlank() }.joinToString("")
}
