package io.esimplified.sdk.repository

import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse

interface PaymentsRepository {
    suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse
}
