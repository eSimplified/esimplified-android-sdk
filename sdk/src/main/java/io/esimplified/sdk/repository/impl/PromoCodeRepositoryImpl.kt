package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PromoCodeRepository

import io.esimplified.sdk.model.CheckoutCouponRequest
import io.esimplified.sdk.model.CheckoutCouponResponse
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.repository.apiRead
import io.esimplified.sdk.repository.apiRejection
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class PromoCodeRepositoryImpl(
    private val apiService: ApiService,
    private val json: Json
) : PromoCodeRepository {

    // region Promo Codes
    override suspend fun addPromoCode(code: String): CheckoutCouponResponse = apiRead {
        val generalResponse = apiService.addCheckoutCoupon(CheckoutCouponRequest(code = code))
        val errorBody = generalResponse.errorBody()?.string()
        val response = generalResponse.body()
            ?: errorBody?.let { json.decodeFromString<CheckoutCouponResponse>(it) }
            ?: throw HttpException(generalResponse)

        if (response.detail != null && !response.valid) {
            throw apiRejection(
                response.detail,
                statusCode = generalResponse.code(),
                apiCode = ApiErrorMessage.code(errorBody),
            )
        }
        response
    }

    override suspend fun getPromoCode(): CheckoutCouponResponse = apiRead {
        val response = apiService.getCheckoutCoupon()
        if (response.detail != null && !response.valid) {
            throw apiRejection(response.detail)
        }
        response
    }

    override suspend fun removePromoCode(): CheckoutCouponResponse =
        apiRead { apiService.removeCheckoutCoupon() }
    // endregion
}
