package io.esimplified.sdk.network

import java.io.IOException

sealed class SdkError(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NetworkError(val statusCode: Int, message: String) : SdkError(message)
    class AuthenticationRequired : SdkError("Authentication required")
    class NoInternetConnection : SdkError("No internet connection")
    class DecodingError(cause: Throwable) : SdkError(GENERIC_FAILURE_MESSAGE, cause)
    class InvalidURL(url: String) : SdkError("Invalid URL: $url")
    class Unknown(cause: Throwable) : SdkError(cause.message ?: "Unknown error", cause)

    val isOffline: Boolean get() = this is NoInternetConnection

    companion object {
        const val GENERIC_FAILURE_MESSAGE: String = "Something went wrong. Please try again."
    }
}
