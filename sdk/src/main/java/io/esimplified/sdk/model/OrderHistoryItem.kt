package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderHistoryItem(
    @SerialName("esim")
    val esim: EsimInfo,
    @SerialName("order_number")
    val orderNumber: Int,
    @SerialName("order_uuid")
    val orderUUID: String,
    @SerialName("order_type")
    val orderType: String,
    @SerialName("package_id")
    val packageId: String,
    @SerialName("final_price")
    val finalPrice: String,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("purchase_date")
    val purchaseDate: String,
    @SerialName("purchase_price")
    val purchasePrice: String,
    @SerialName("discount_code")
    val discountCode: String,
    @SerialName("discount_amount")
    val discountAmount: String,
    @SerialName("purchase_currency")
    val purchaseCurrency: String,
    @SerialName("purchase_currency_obj")
    val purchaseCurrencyObject: CurrencyObject,
    @SerialName("package_type_id")
    val packageTypeId: Int,
    @SerialName("payment_status")
    val paymentStatus: String,
    @SerialName("payment_method")
    val paymentMethod: PaymentMethod,
    @SerialName("country")
    val country: Country,
    @SerialName("points_earned")
    val loyaltyPointsEarned: LoyaltyPointsDetail? = null,
    @SerialName("points_spent")
    val loyaltyPointsSpent: LoyaltyPointsDetail? = null
)

@Serializable
data class PurchaseCountry(
    @SerialName("iso")
    val iso: String,
    @SerialName("name")
    val name: String,
    @SerialName("iso3")
    val iso3: String,
    @SerialName("flag")
    val flag: String,
    @SerialName("is_region")
    val isRegion: Boolean
)
