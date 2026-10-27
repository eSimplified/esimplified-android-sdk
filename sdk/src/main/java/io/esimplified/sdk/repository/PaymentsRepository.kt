package io.esimplified.sdk.repository

import io.esimplified.sdk.model.PaymentMethodSetupIntent
import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.model.SavedPaymentMethod

interface PaymentsRepository {
    suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse

    // region Saved payment methods
    suspend fun getPaymentMethods(discountCode: String? = null): List<SavedPaymentMethod> =
        throw UnsupportedOperationException("getPaymentMethods is not implemented")

    suspend fun deletePaymentMethod(paymentMethodId: String) {
        throw UnsupportedOperationException("deletePaymentMethod is not implemented")
    }

    suspend fun createPaymentMethodSetupIntent(): PaymentMethodSetupIntent =
        throw UnsupportedOperationException("createPaymentMethodSetupIntent is not implemented")

    suspend fun verifyPaymentMethod(
        paymentMethodId: String,
        discountCode: String,
    ): SavedPaymentMethod =
        throw UnsupportedOperationException("verifyPaymentMethod is not implemented")

    suspend fun payWithStoredPaymentMethod(
        paymentMethodId: String,
        transactionType: String,
        packageTypeId: Int,
        iccid: String? = null,
        discountCode: String? = null,
    ): PaymentResponse =
        throw UnsupportedOperationException("payWithStoredPaymentMethod is not implemented")
    // endregion
}
