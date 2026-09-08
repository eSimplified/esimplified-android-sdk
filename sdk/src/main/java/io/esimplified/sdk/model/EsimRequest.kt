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
    @SerialName("profile") val profile: EsimProfile? = null,
    @SerialName("assigned_date") val assignedDate: String,
    @SerialName("package_details") val packages: List<PackageDetail> = listOf(),
    @SerialName("data_usage_remaining_bytes") val dataUsageRemainingBytes: Double = 0.0,
    @SerialName("data_usage_remaining_gigabytes") val dataUsageRemainingGigabytes: Double = 0.0,
    @SerialName("archived") val isArchived: Boolean,
    @SerialName("auto_top_up") val isAutoTopUp: Boolean,
    @SerialName("android_sha") val androidSha: Boolean = false,
    @SerialName("order_number") val orderNumber: String? = null,
    @SerialName("date_activated_epoch") val dateActivatedEpoch: Long? = null,
    @SerialName("date_expiry_epoch") val dateExpiryEpoch: Long? = null,
    @SerialName("days_left_to_expiry") val daysLeftToExpiry: Int? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("is_universal") val isUniversal: Boolean = false,
)
