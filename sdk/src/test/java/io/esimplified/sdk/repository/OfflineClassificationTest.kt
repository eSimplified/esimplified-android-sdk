package io.esimplified.sdk.repository

import io.esimplified.sdk.network.SdkError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineClassificationTest {

    // region Offline
    @Test
    fun `UnknownHostException is classified as no internet connection`() {
        val error = UnknownHostException("api.esimplified.io").asSdkError()

        assertTrue(error is SdkError.NoInternetConnection)
        assertTrue(error.isOffline)
    }

    @Test
    fun `ConnectException is classified as no internet connection`() {
        val error = ConnectException("Failed to connect").asSdkError()

        assertTrue(error is SdkError.NoInternetConnection)
        assertTrue(error.isOffline)
    }

    @Test
    fun `a wrapped UnknownHostException is still classified as offline`() {
        val error = IOException("request failed", UnknownHostException("api")).asSdkError()

        assertTrue(error is SdkError.NoInternetConnection)
        assertTrue(error.isOffline)
    }

    @Test
    fun `an offline failure surfaces through RepositoryResult`() {
        val result = RepositoryResult(
            value = "cached",
            isStale = true,
            failure = UnknownHostException("api").asSdkError(),
        )

        assertTrue(result.didFail)
        assertTrue(result.isOffline)
    }
    // endregion

    // region Not offline
    @Test
    fun `SocketTimeoutException is not classified as no internet connection`() {
        val error = SocketTimeoutException("timeout").asSdkError()

        assertTrue(error is SdkError.Unknown)
        assertFalse(error.isOffline)
    }

    @Test
    fun `a wrapped SocketTimeoutException is not classified as offline`() {
        val error = IOException("request failed", SocketTimeoutException("timeout")).asSdkError()

        assertTrue(error is SdkError.Unknown)
        assertFalse(error.isOffline)
    }

    @Test
    fun `a plain IOException is not classified as offline`() {
        val error = IOException("broken pipe").asSdkError()

        assertTrue(error is SdkError.Unknown)
        assertFalse(error.isOffline)
    }

    @Test
    fun `an existing SdkError is passed through unchanged`() {
        val original = SdkError.NetworkError(statusCode = 500, message = "server exploded")

        assertTrue(original.asSdkError() === original)
        assertFalse(original.asSdkError().isOffline)
    }
    // endregion
}
