package io.esimplified.sdk.network

class PaymentApiException(
    val httpCode: Int,
    val type: String?,
    override val message: String?,
) : Exception(message) {

    companion object {
        const val TYPE_VALIDATION_ERROR = "VALIDATION_ERROR"
    }
}
