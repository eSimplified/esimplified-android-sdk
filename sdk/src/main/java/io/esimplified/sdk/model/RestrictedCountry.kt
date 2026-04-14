package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class RestrictionType {
    @SerialName("global")
    GLOBAL,
    
    @SerialName("local")
    LOCAL
}

@Serializable
data class RestrictedFor(
    @SerialName("country_code")
    val countryCode: String,
    
    @SerialName("country_name")
    val countryName: String
)

@Serializable
data class RestrictedCountry(
    @SerialName("country_code")
    val countryCode: String,
    
    @SerialName("restriction_type")
    val restrictionType: RestrictionType,
    
    @SerialName("restricted_for")
    val restrictedFor: List<RestrictedFor>? = null
)

