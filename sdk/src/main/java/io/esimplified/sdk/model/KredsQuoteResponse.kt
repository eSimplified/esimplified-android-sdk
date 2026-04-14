package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KredsQuoteResponse(
    @SerialName("package_type_id") val packageTypeId: Int? = null,
    @SerialName("currency") val currency: CurrencyObject? = null,
    @SerialName("preferred_currency") val preferredCurrency: CurrencyObject? = null,
    @SerialName("pricing") val pricing: KredsQuotePricing,
    @SerialName("points") val points: KredsQuotePoints,
)

@Serializable
data class KredsQuotePricing(
    @SerialName("order_currency") val orderCurrency: KredsQuoteOrderCurrency,
    @SerialName("usd") val usd: KredsQuoteUsdPricing? = null,
    @SerialName("preferred_currency") val preferredCurrency: KredsQuotePreferredPricing? = null,
)

@Serializable
data class KredsQuoteOrderCurrency(
    @SerialName("exchange_rate_to_usd") val exchangeRateToUsd: String? = null,
    @SerialName("subtotal") val subtotal: String? = null,
    @SerialName("package_discount") val packageDiscount: String? = null,
    @SerialName("promo_discount") val promoDiscount: String? = null,
    @SerialName("points_applied") val pointsApplied: String? = null,
    @SerialName("total") val total: String,
    @SerialName("currency") val currency: CurrencyObject? = null,
)

@Serializable
data class KredsQuoteUsdPricing(
    @SerialName("subtotal") val subtotal: String? = null,
    @SerialName("package_discount") val packageDiscount: String? = null,
    @SerialName("promo_discount") val promoDiscount: String? = null,
    @SerialName("points_applied") val pointsApplied: String? = null,
    @SerialName("total") val total: String,
    @SerialName("currency") val currency: CurrencyObject? = null,
)

@Serializable
data class KredsQuotePreferredPricing(
    @SerialName("total") val total: String,
    @SerialName("currency") val currency: CurrencyObject? = null,
)

@Serializable
data class KredsQuotePoints(
    @SerialName("requested_cents") val requestedCents: Int? = null,
    @SerialName("applied_cents") val appliedCents: Int? = null,
    @SerialName("applied_value") val appliedValue: KredsQuoteValue? = null,
    @SerialName("applied_value_usd") val appliedValueUsd: KredsQuoteValue? = null,
    @SerialName("applied_value_preferred") val appliedValuePreferred: KredsQuoteValue? = null,
)

@Serializable
data class KredsQuoteValue(
    @SerialName("amount") val amount: String,
    @SerialName("currency") val currency: CurrencyObject,
)
