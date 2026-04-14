package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.network.ApiService

internal class PaymentsRepositoryImpl(
    private val apiService: ApiService
) : PaymentsRepository {

    // region Payments
    override suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse {
        val response = apiService.getCheckoutPaymentIntent(request)
        if (response.detail != null && response.transaction == null) {
            throw Exception(response.detail)
        }
        return response
    }
    // endregion
}
