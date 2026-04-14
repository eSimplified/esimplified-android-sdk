package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckStockResponse(
    @SerialName("stock") val stock: Boolean,
    @SerialName("package") val packageInfo: PackagePlan,
    @SerialName("promo_code") val promoCode: CheckoutCouponResponse = CheckoutCouponResponse()
)
