package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PackageDetail(
    val status: String,
    @SerialName("package_id")
    val packageID: String? = null,
    @SerialName("package_type_id")
    val packageTypeID: Long? = null,
    @SerialName("date_expiry_epoch")
    val dateExpiryEpoch: Long? = null,
    @SerialName("date_activated_epoch")
    val dateActivatedEpoch: Long? = null,
    @SerialName("date_terminated_epoch")
    val dateTerminatedEpoch: Long? = null,
    @SerialName("supported_countries")
    val supportedCountries: List<SupportedCountry> = listOf(),
    @SerialName("date_created_utc")
    val dateCreatedUTC: String? = null,
    @SerialName("date_created_epoch")
    val dateCreatedEpoch: Long,
    @SerialName("package_country_name")
    val packageCountryName: String,
    @SerialName("voice_usage_remaining_seconds")
    val voiceUsageRemainingSeconds: Long = 0,
    @SerialName("data_usage_remaining_gigabytes")
    val dataUsageRemainingGigabytes: Double = 0.0,
    @SerialName("data_usage_remaining_bytes")
    val dataUsageRemainingBytes: Double = 0.0,
    @SerialName("data_allowance_gigabytes")
    val dataAllowanceGigabytes: Double = 0.0,
    @SerialName("data_allowance_bytes")
    val dataAllowanceBytes: Double = 0.0,
    @SerialName("sms_usage_remaining_nums")
    val smsUsageRemainingNums: Int = 0,
    @SerialName("window_activation_start_epoch")
    val windowActivationStartEpoch: Long = 0,
    @SerialName("window_activation_end_epoch")
    val windowActivationEndEpoch: Long = 0,
    @SerialName("window_activation_start_utc")
    val windowActivationStartUtc: String? = null,
    @SerialName("window_activation_end_utc")
    val windowActivationEndUtc: String? = null,
    @SerialName("time_allowance_seconds")
    val timeAllowanceSeconds: Double = 0.0,
    @SerialName("time_allowance_days")
    val timeAllowanceDays: Double = 0.0
)
