package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.VouchersRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.VoucherRedeemRequest
import io.esimplified.sdk.model.VoucherRedeemResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import io.esimplified.sdk.SdkLog

internal class VouchersRepositoryImpl(
    private val apiService: ApiService
) : VouchersRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun redeemVoucher(code: String): Result<VoucherRedeemResponse> {
        return try {
            val request = VoucherRedeemRequest(voucherCode = code)
            SdkLog.d("Attempting to redeem a voucher")
            val response = apiService.redeemVoucher(request)
            SdkLog.d("Voucher redemption returned redeemed=${response.redeemed}")
            Result.success(response)
        } catch (e: HttpException) {
            val errorMessage = try {
                val errorBody = e.response()?.errorBody()?.string()
                SdkLog.d("Voucher redemption rejected with HTTP ${e.code()}")

                if (!errorBody.isNullOrEmpty()) {
                    val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                    val message = errorResponse.detail
                        ?: errorResponse.message
                        ?: errorResponse.error
                        ?: e.message()
                    SdkLog.d("Voucher redemption carried a server error message")
                    message
                } else {
                    SdkLog.d("Empty error body, no specific error message from server")
                    null
                }
            } catch (parseError: Exception) {
                SdkLog.e("Error parsing error response", parseError)
                null
            }
            SdkLog.e("Error redeeming voucher (HTTP ${e.code()})", e)
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            SdkLog.e("Error redeeming voucher", e)
            Result.failure(Exception(e.message))
        }
    }
}
