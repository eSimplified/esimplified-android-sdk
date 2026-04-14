package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderInfo(
    val detail: String? = null,
    @SerialName("customer")
    val customer: Customer,
    @SerialName("discount_amount")
    val discountAmount: String,
    @SerialName("discount_code")
    val discountCode: String,
    @SerialName("esim")
    val esim: EsimInfo,
    @SerialName("final_price")
    val finalPrice: String,
    @SerialName("iccid")
    val iccid: String,
    @SerialName("order_type")
    val orderType: String,
    @SerialName("order_uuid")
    val orderUuid: String,
    @SerialName("package_id")
    val packageId: String,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("package_type_id")
    val packageTypeId: Int,
    @SerialName("purchase_country")
    val purchaseCountry: Country,
    @SerialName("purchase_currency")
    val purchaseCurrency: String,
    @SerialName("purchase_date")
    val purchaseDate: String,
    @SerialName("purchase_price")
    val purchasePrice: String,
    @SerialName("qr_code")
    val qrCode: QrCode
)
