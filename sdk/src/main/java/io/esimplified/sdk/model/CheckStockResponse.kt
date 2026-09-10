package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CheckStockResponse(
    @SerialName("stock") val stock: Boolean = false,
    @SerialName("package") val packageInfo: PackagePlan? = null,
    @SerialName("promo_code") val promoCode: CheckoutCouponResponse = CheckoutCouponResponse()
)
