package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PackagePlan(
    @SerialName("name") val name: String = "",
    @SerialName("price")
    @Serializable(with = LenientStringSerializer::class)
    val price: String,
    @SerialName("converted_price") val convertedPrice: Double? = null,
    @SerialName("data_GB") val data: Double,
    @SerialName("country")
    @Serializable(with = TolerantCountrySerializer::class)
    val country: Country = Country(),
    @SerialName("network") val network: List<String>? = emptyList(),
    @SerialName("currency") val currency: String = "",
    @SerialName("currency_obj") val currencyObject: CurrencyObject = CurrencyObject(),
    @SerialName("plan_type") val planType: String = "",
    @SerialName("kyc_display") val kycDisplay: String = "",
    @SerialName("package_slug") val packageSlug: String = "",
    @SerialName("validity_days") val validityDays: Long,
    @SerialName("package_type_id") val packageTypeId: Long,
    @SerialName("best_connectivity") val bestConnectivity: String = "",
    @SerialName("activation_policy") val activationPolicy: String = "",
    @SerialName("supported_countries")
    @Serializable(with = TolerantSupportedCountriesSerializer::class)
    val supportedCountries: List<SupportedCountry> = listOf(),
    @SerialName("name_additional_text") val nameAdditionalText: String = "",
    @SerialName("discounted_price")
    @Serializable(with = LenientStringSerializer::class)
    val discountedPrice: String? = null,
    @SerialName("earn_percentage") val earnPercentage: Double? = null,
    @SerialName("data_cap") val dataCap: String? = null,
    @SerialName("throttle_speed") val throttleSpeed: String? = null,
    @SerialName("validity_days_display") val validityDaysDisplay: String = "",
    @SerialName("discount_label") val discountLabel: String = "",
    @SerialName("discount_percentage") val discountPercentage: String? = null,
    @SerialName("promo_code") val promoCode: CheckoutCouponResponse? = null
) {
    val isUnlimited: Boolean = data in listOf(-1.0, -1)

    // region Money
    val priceValue: Double
        get() = price.toDoubleOrNull() ?: 0.0

    val discountedPriceValue: Double?
        get() = discountedPrice?.toDoubleOrNull()

    val purchasePrice: String
        get() = discountedPrice ?: price

    val purchasePriceValue: Double
        get() = purchasePrice.toDoubleOrNull() ?: 0.0

    val isFreePurchase: Boolean
        get() = purchasePriceValue == 0.0

    val hasDiscount: Boolean
        get() = (discountedPriceValue ?: 0.0) > 0.0
    // endregion
}
