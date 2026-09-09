package io.esimplified.sdk.network

import io.esimplified.sdk.model.CheckoutCouponResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BaseResponse<T>(
    val count: Int = 0,
    val next: String? = null,
    val previous: String? = null,
    val results: T,
    @SerialName("promo_code") val promoCode: CheckoutCouponResponse? = null,
)
