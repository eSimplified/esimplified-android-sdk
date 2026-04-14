package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.VoucherRedeemRequest
import io.esimplified.sdk.model.VoucherRedeemResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber

internal class VouchersRepositoryImpl(
    private val apiService: ApiService
) : VouchersRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun redeemVoucher(code: String): Result<VoucherRedeemResponse> {
        return try {
            val request = VoucherRedeemRequest(voucherCode = code)
            Timber.d("Attempting to redeem voucher with code: $code")
            val response = apiService.redeemVoucher(request)
            Timber.d("Successfully redeemed voucher: redeemed=${response.redeemed}, orderUUID=${response.orderUUID}")
            Result.success(response)
        } catch (e: HttpException) {
            val errorMessage = try {
                val errorBody = e.response()?.errorBody()?.string()
                Timber.d("HTTP ${e.code()} error body: $errorBody")

                if (!errorBody.isNullOrEmpty()) {
                    val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                    val message = errorResponse.detail
                        ?: errorResponse.message
                        ?: errorResponse.error
                        ?: e.message()
                    Timber.d("Parsed error message: $message")
                    message
                } else {
                    Timber.d("Empty error body, no specific error message from server")
                    null
                }
            } catch (parseError: Exception) {
                Timber.e(parseError, "Error parsing error response")
                null
            }
            Timber.e(e, "Error redeeming voucher (HTTP ${e.code()}): $errorMessage")
            Result.failure(Exception(errorMessage))
        } catch (e: Exception) {
            Timber.e(e, "Error redeeming voucher: ${e.message}")
            Result.failure(Exception(e.message))
        }
    }
}
