package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EsimPackageListRequest(
    @SerialName("iccid") val iccid: String,
    @SerialName("customer_id") val customerId: String,
)
