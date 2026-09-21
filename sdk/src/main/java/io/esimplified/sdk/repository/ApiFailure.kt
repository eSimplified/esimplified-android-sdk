package io.esimplified.sdk.repository

import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.SdkError
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

// region Running a call
internal inline fun <T> apiRead(fetch: () -> T): T =
    try {
        fetch()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        throw failure.asSdkError()
    }
// endregion

// region Classifying a failure
internal fun Throwable.asSdkError(): SdkError = when {
    this is SdkError -> this
    this is HttpException -> asNetworkError()
    this is SerializationException -> SdkError.DecodingError(this)
    isOfflineFailure() -> SdkError.NoInternetConnection()
    else -> SdkError.Unknown(this)
}

internal fun HttpException.asNetworkError(fallback: String? = null): SdkError.NetworkError =
    SdkError.NetworkError(code(), ApiErrorMessage.parseOrNull(this) ?: fallback.orFallback(message()))

internal fun apiRejection(
    message: String?,
    fallback: String? = null,
    statusCode: Int = HttpURLConnection.HTTP_OK,
): SdkError.NetworkError = SdkError.NetworkError(statusCode, message.orFallback(fallback))

private fun String?.orFallback(fallback: String?): String =
    this?.trim()?.ifEmpty { null }
        ?: fallback?.trim()?.ifEmpty { null }
        ?: ApiErrorMessage.FALLBACK

private fun Throwable.isOfflineFailure(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < 8) {
        if (current is UnknownHostException || current is ConnectException) return true
        current = current.cause.takeIf { it !== current }
        depth++
    }
    return false
}
// endregion

// region Unwrapping
internal fun SdkError?.originalOrSelf(): Throwable = when (this) {
    null -> SdkError.Unknown(IllegalStateException("No cached value and no failure recorded"))
    is SdkError.Unknown -> cause ?: this
    else -> this
}
// endregion
