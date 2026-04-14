package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PaymentRequest(
    @SerialName("type") val type: String,
    @SerialName("iccid") val iccid: String? = null,
    @SerialName("customer") val customer: CustomerDetails,
    @SerialName("package_type_id") val packageTypeId: Int,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("auto_top_up") val autoTopUp: Boolean,
    @SerialName("save_payment_method") val savePaymentMethod: Boolean,
    @SerialName("loyalty_points_amount") val loyaltyPointsAmount: Double? = null,
) {

    object Type {
        const val BUY = "buy"
        const val TOP_UP = "top-up"
    }

    object Method {
        const val STRIPE_INTENT = "stripe_intent"
        const val STRIPE_CHECKOUT = "stripe_checkout"
    }
}

@Serializable
data class Transaction(
    /** link to payment gateway or the intent secret for stripe */
    @SerialName("uri") val uri: String? = null,
    @SerialName("order_id") val orderId: String? = "",
    @SerialName("zero_charge") val zeroCharge: Boolean = false,
    @SerialName("customer_ref") val customerRef: String? = null,
    @SerialName("ephemeral_key") val ephemeralKey: String? = null,
    @SerialName("publishable_key") val publishableKey: String? = null,
    @SerialName("is_intent") val isIntent: Boolean = false,
)

@Serializable
data class PaymentResponse(
    /** link to payment gateway or the intent secret for stripe */
    val detail: String? = null,
    @SerialName("data") val transaction: Transaction? = null,
)


@Serializable
data class CheckoutCouponRequest(
    @SerialName("promo_code")
    val code: String? = null,
)

@Serializable
data class CheckoutCouponResponse(
    @SerialName("valid")
    val valid: Boolean = false,
    @SerialName("detail")
    val detail: String? = null,
    @SerialName("discount_code")
    val discount: String? = null,
    @SerialName("discount_percentage")
    val percentage: Double? = null,
    @SerialName("product_type")
    val productType: String? = null
) {

    companion object {
        private const val PRODUCT_TYPE_VIFR = "VIFR"
    }

    val isRemovable: Boolean =
        (valid && (percentage ?: 0.0) > 0) && productType != PRODUCT_TYPE_VIFR
}
