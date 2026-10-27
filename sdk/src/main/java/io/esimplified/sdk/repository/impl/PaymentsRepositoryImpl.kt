package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PaymentsRepository

import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.model.CustomerDetails
import io.esimplified.sdk.model.PaymentMethodSetupIntent
import io.esimplified.sdk.model.PaymentRequest
import io.esimplified.sdk.model.PaymentResponse
import io.esimplified.sdk.model.SavedPaymentMethod
import io.esimplified.sdk.model.VerifyPaymentMethodRequest
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.PaymentApiException
import io.esimplified.sdk.repository.apiRejection
import io.esimplified.sdk.repository.asNetworkError
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import retrofit2.HttpException

internal class PaymentsRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
) : PaymentsRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Payments
    override suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse {
        try {
            val response = apiService.getCheckoutPaymentIntent(request)
            if (response.detail != null && response.transaction == null) {
                throw apiRejection(response.detail)
            }
            return response
        } catch (e: HttpException) {
            val errorBody = ApiErrorMessage.body(e)
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
            throw e.asNetworkError(parsed?.detail, errorBody)
        }
    }
    // endregion

    // region Saved payment methods
    override suspend fun getPaymentMethods(discountCode: String?): List<SavedPaymentMethod> =
        try {
            apiService.getPaymentMethods(discountCode = discountCode.orNullIfBlank())
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }

    override suspend fun deletePaymentMethod(paymentMethodId: String) {
        val response = apiService.deletePaymentMethod(paymentMethodId = paymentMethodId)
        if (!response.isSuccessful) {
            val errorBody = readBody(response.errorBody())
            throw apiRejection(
                ApiErrorMessage.parse(errorBody),
                statusCode = response.code(),
                apiCode = ApiErrorMessage.code(errorBody),
            )
        }
    }

    override suspend fun createPaymentMethodSetupIntent(): PaymentMethodSetupIntent =
        try {
            apiService.createPaymentMethodSetupIntent()
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }

    override suspend fun verifyPaymentMethod(paymentMethodId: String, discountCode: String): SavedPaymentMethod =
        try {
            apiService.verifyPaymentMethod(
                VerifyPaymentMethodRequest(paymentMethodId = paymentMethodId, discountCode = discountCode)
            )
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }

    override suspend fun payWithStoredPaymentMethod(
        paymentMethodId: String,
        transactionType: String,
        packageTypeId: Int,
        iccid: String?,
        discountCode: String?,
    ): PaymentResponse = getPaymentIntent(
        PaymentRequest(
            type = transactionType,
            iccid = iccid,
            customer = CustomerDetails(email = userEmail()),
            packageTypeId = packageTypeId,
            paymentMethod = PaymentRequest.Method.STRIPE_INTENT,
            storedPaymentMethod = paymentMethodId,
            discountCode = discountCode.orNullIfBlank(),
        )
    )

    private fun userEmail(): String =
        (sessionManager.getAuthState() as? Auth.Authenticated)?.user?.email.orEmpty()

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun readBody(body: ResponseBody?): String? =
        runCatching { body?.string() }.getOrNull()
    // endregion
}
