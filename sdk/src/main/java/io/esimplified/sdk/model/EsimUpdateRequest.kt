package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class EsimUpdateRequest(
    @SerialName("esim_name") val name: String? = null,
    @SerialName("archived") val isArchived: Boolean? = null,
    @SerialName("auto_top_up") val autoTopUp: Boolean? = null,
    @SerialName("is_primary") val isPrimary: Boolean? = null,
)
