package io.esimplified.sdk.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationSettings(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("type")
    val type: String = "",
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("enabled")
    val enabled: Boolean = false,
)
