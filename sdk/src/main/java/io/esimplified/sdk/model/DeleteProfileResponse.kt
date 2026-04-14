package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteProfileResponse(
    @SerialName("deleted") var deleted: Boolean = false
)
