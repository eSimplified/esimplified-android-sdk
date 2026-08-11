package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PaymentsRepository

import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.PaymentApiException
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
            val parsed = errorBody?.let {
                try {
                    json.decodeFromString<PaymentResponse>(it)
                } catch (_: Exception) {
                    null
                }
            }
            if (parsed?.type != null) {
                throw PaymentApiException(e.code(), parsed.type, parsed.message ?: parsed.detail)
            }
            throw Exception(parsed?.detail ?: e.message)
        }
    }
    // endregion
}
