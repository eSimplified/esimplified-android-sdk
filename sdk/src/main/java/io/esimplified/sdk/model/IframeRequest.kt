package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IframeRequest(
    @SerialName("vendor") val vendor: String? = null
)
