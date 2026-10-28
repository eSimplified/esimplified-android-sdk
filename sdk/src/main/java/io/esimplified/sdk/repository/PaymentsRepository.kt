package io.esimplified.sdk.repository

import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.model.SavedPaymentMethod

interface PaymentsRepository {
    suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse

    // region Saved payment methods
    suspend fun getPaymentMethods(): List<SavedPaymentMethod> =
        throw UnsupportedOperationException("getPaymentMethods is not implemented")

    suspend fun payWithStoredPaymentMethod(
        paymentMethodId: String,
        transactionType: String,
        packageTypeId: Int,
        iccid: String? = null,
    ): PaymentResponse =
        throw UnsupportedOperationException("payWithStoredPaymentMethod is not implemented")
    // endregion

    // region SmartPay
    suspend fun startSmartPayPayment(
        transactionType: String,
        packageTypeId: Int,
        iccid: String? = null,
        loyaltyPointsAmount: Double? = null,
        redirectUrl: String,
    ): PaymentResponse =
        throw UnsupportedOperationException("startSmartPayPayment is not implemented")
    // endregion
}
