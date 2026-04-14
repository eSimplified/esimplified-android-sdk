package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationSettings(
    @SerialName("type")
    val type: String,
    @SerialName("enabled")
    val enabled: Boolean
)
