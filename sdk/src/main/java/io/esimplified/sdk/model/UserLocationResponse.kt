package io.esimplified.sdk.model

import kotlinx.serialization.Serializable

@Serializable
data class UserLocationResponse(
    val location: LocationDetails?
)

@Serializable
data class LocationDetails(
    val country: String?,
    val countryCode: String?,
    val city: String?,
    val lat: Double?,
    val lon: Double?,
    val timezone: String?
)
