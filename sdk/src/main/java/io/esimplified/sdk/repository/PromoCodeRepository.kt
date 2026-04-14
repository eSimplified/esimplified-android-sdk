package io.esimplified.sdk.repository

import io.esimplified.sdk.model.CheckoutCouponResponse

interface PromoCodeRepository {
    suspend fun addPromoCode(code: String): CheckoutCouponResponse
    suspend fun getPromoCode(): CheckoutCouponResponse
    suspend fun removePromoCode(): CheckoutCouponResponse
}
