package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EsimRequest(
    @SerialName("customer_id") val customerId: String,
    @SerialName("show_package_details") val getPackageDetails: Boolean,
    @SerialName("show_balance_remaining") val getBalanceRemaining: Boolean
)

@Serializable
data class AssignedEsim(
    @SerialName("iccid")
    val iccid: String,
    @SerialName("esim_name")
    val name: String?,
    @SerialName("country")
    val country: Country? = null,
    @SerialName("order_uuid")
    val orderUUID: String? = null,
    @SerialName("profile") val profile: EsimProfile,
    @SerialName("assigned_date") val assignedDate: String,
    @SerialName("package_details") val packages: List<PackageDetail> = listOf(),
    @SerialName("data_usage_remaining_bytes") val dataUsageRemainingBytes: Double = 0.0,
    @SerialName("data_usage_remaining_gigabytes") val dataUsageRemainingGigabytes: Double = 0.0,
    @SerialName("archived") val isArchived: Boolean,
    @SerialName("auto_top_up") val isAutoTopUp: Boolean,
)
