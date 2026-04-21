package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PaymentsRepository

import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class PaymentsRepositoryImpl(
    private val apiService: ApiService
) : PaymentsRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Payments
    override suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse {
        try {
            val response = apiService.getCheckoutPaymentIntent(request)
            if (response.detail != null && response.transaction == null) {
                throw Exception(response.detail)
            }
            return response
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val message = if (errorBody != null) {
                try {
                    json.decodeFromString<PaymentResponse>(errorBody).detail
                } catch (_: Exception) {
                    e.message
                }
            } else {
                e.message
            }
            throw Exception(message)
        }
    }
    // endregion
}
