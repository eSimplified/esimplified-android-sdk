package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoyaltyPointsOriginal(
    @SerialName("amount_usd") val amountUSD: String,
    @SerialName("currency") val currency: CurrencyObject,
)

@Serializable
data class LoyaltyPointsDetail(
    @SerialName("amount") val amount: String? = null,
    @SerialName("amount_local_currency") val amountLocalCurrency: String? = null,
    @SerialName("amount_local_currency_cents") val amountLocalCurrencyCents: Int? = null,
    @SerialName("currency") val currency: CurrencyObject,
    @SerialName("original") val original: LoyaltyPointsOriginal? = null,
) {
    /**
     * Mirrors iOS fallback logic:
     * 1. explicit `amount` field
     * 2. `amount_local_currency` (preferred currency conversion)
     * 3. `original.amount_usd` (USD fallback)
     * 4. "0.00"
     */
    val resolvedAmount: String
        get() = amount?.takeIf { it.isNotEmpty() }
            ?: amountLocalCurrency?.takeIf { it.isNotEmpty() }
            ?: original?.amountUSD?.takeIf { it.isNotEmpty() }
            ?: "0.00"

    /**
     * Mirrors iOS: kredsCurrencyISO = response.totalLoyaltyPointsDetail.currency.iso
     * The `currency` field is already the preferred currency as the server uses the
     * accept-currency request header to populate it.
     */
    val resolvedCurrencyIso: String
        get() = currency.isoCode
}

@Serializable
data class KredsLoyaltyBalanceResponse(
    @SerialName("total_loyalty_points")
    val totalLoyaltyPoints: Int,
    @SerialName("total_loyalty_points_detail")
    val totalLoyaltyPointsDetail: LoyaltyPointsDetail,
)
