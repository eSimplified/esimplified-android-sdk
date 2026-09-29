package io.esimplified.sdk.network

import java.io.IOException

sealed class SdkError(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NetworkError(
        val statusCode: Int,
        message: String,
        override val apiCode: String? = null,
    ) : SdkError(message)
    class AuthenticationRequired : SdkError("Authentication required")
    class NoInternetConnection : SdkError("No internet connection")
    class DecodingError(cause: Throwable) : SdkError(GENERIC_FAILURE_MESSAGE, cause)
    class InvalidURL(url: String) : SdkError("Invalid URL: $url")
    class Unknown(cause: Throwable) : SdkError(cause.message ?: "Unknown error", cause)

    open val apiCode: String? get() = null

    val isOffline: Boolean get() = this is NoInternetConnection

    fun hasApiCode(code: ApiErrorCode): Boolean = apiCode == code.value

    val isEmailNotVerified: Boolean
        get() = hasApiCode(ApiErrorCode.EMAIL_NOT_VERIFIED) ||
            (apiCode == OAUTH_INVALID_GRANT && message?.contains(EMAIL_NOT_VERIFIED_MARKER, ignoreCase = true) == true)

    companion object {
        const val GENERIC_FAILURE_MESSAGE: String = "Something went wrong. Please try again."
        private const val OAUTH_INVALID_GRANT = "invalid_grant"
        private const val EMAIL_NOT_VERIFIED_MARKER = "not verified"
    }
}

enum class ApiErrorCode(val value: String) {
    INVALID_CODE("invalid_code"),
    CODE_EXPIRED("code_expired"),
    PHONE_ALREADY_VERIFIED("phone_already_verified"),
    NO_PENDING_VERIFICATION("no_pending_verification"),
    TOO_MANY_REQUESTS("too_many_requests"),
    PROVIDER_ERROR("provider_error"),
    PHONE_VERIFICATION_REQUIRED("phone_verification_required"),
    EMAIL_NOT_VERIFIED("email_not_verified"),
}
