package io.esimplified.sdk.network

import java.io.IOException

sealed class SdkError(message: String, cause: Throwable? = null) : IOException(message, cause) {
    class NetworkError(val statusCode: Int, message: String) : SdkError("HTTP $statusCode: $message")
    class AuthenticationRequired : SdkError("Authentication required")
    class DecodingError(cause: Throwable) : SdkError("Failed to decode response: ${cause.message}", cause)
    class InvalidURL(url: String) : SdkError("Invalid URL: $url")
    class Unknown(cause: Throwable) : SdkError(cause.message ?: "Unknown error", cause)
}
