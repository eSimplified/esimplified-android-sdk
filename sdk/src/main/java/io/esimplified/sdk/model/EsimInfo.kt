package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EsimInfo(
    @SerialName("assigned_date")
    val assignedDate: String,
    @SerialName("iccid")
    val iccid: String,
    @SerialName("matching_id")
    val matchingId: String,
    @SerialName("premium")
    val premium: Boolean,
    @SerialName("sm_dp_address")
    val smDpAddress: String,
)
