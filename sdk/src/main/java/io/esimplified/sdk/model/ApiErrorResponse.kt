package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorResponse(
    @SerialName("detail") val detail: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null
)

