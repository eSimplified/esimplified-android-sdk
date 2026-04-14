package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PackagePlan(
    @SerialName("name") val name: String,
    @SerialName("price") val price: Double,
    @SerialName("converted_price") val convertedPrice: Double? = null,
    @SerialName("data_GB") val data: Double,
    @SerialName("country") val country: Country,
    @SerialName("network") val network: List<String>? = emptyList(),
    @SerialName("currency") val currency: String,
    @SerialName("currency_obj") val currencyObject: CurrencyObject,
    @SerialName("plan_type") val planType: String,
    @SerialName("kyc_display") val kycDisplay: String,
    @SerialName("package_slug") val packageSlug: String,
    @SerialName("validity_days") val validityDays: Long,
    @SerialName("package_type_id") val packageTypeId: Long,
    @SerialName("best_connectivity") val bestConnectivity: String,
    @SerialName("activation_policy") val activationPolicy: String,
    @SerialName("supported_countries") val supportedCountries: List<SupportedCountry>,
    @SerialName("name_additional_text") val nameAdditionalText: String,
    @SerialName("discounted_price") val discountedPrice: Double? = null,
    @SerialName("earn_percentage") val earnPercentage: Double? = null
) {
    val isUnlimited: Boolean = data in listOf(-1.0, -1)
    val purchasePrice: Double = discountedPrice ?: price
    val isFreePurchase: Boolean = purchasePrice == 0.00
    val hasDiscount: Boolean = discountedPrice?.let { it > 0.00 } ?: false
}
