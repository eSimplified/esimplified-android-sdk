package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.CheckoutCouponRequest
import io.esimplified.sdk.model.CheckoutCouponResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class PromoCodeRepositoryImpl(
    private val apiService: ApiService,
    private val json: Json
) : PromoCodeRepository {

    // region Promo Codes
    override suspend fun addPromoCode(code: String): CheckoutCouponResponse {
        val generalResponse = apiService.addCheckoutCoupon(CheckoutCouponRequest(code = code))
        val response = generalResponse.body()
            ?: generalResponse.errorBody()?.string()
                ?.let { errorBody -> json.decodeFromString(errorBody) }
            ?: throw HttpException(generalResponse)

        if (response.detail != null && !response.valid) {
            throw Exception(response.detail)
        }
        return response
    }

    override suspend fun getPromoCode(): CheckoutCouponResponse {
        val response = apiService.getCheckoutCoupon()
        if (response.detail != null && !response.valid) {
            throw Exception(response.detail)
        }
        return response
    }

    override suspend fun removePromoCode(): CheckoutCouponResponse {
        val response = apiService.removeCheckoutCoupon()
        // Don't throw on removal success - valid=false with detail just means coupon was removed
        return response
    }
    // endregion
}
