package io.esimplified.sdk.repository

import io.esimplified.sdk.network.SdkError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryResultTest {

    @Test
    fun `a fresh result is neither stale nor failed`() {
        val result = RepositoryResult(value = listOf("ZA"))

        assertEquals(listOf("ZA"), result.value)
        assertFalse(result.isStale)
        assertNull(result.failure)
        assertFalse(result.didFail)
        assertFalse(result.isOffline)
    }

    @Test
    fun `isStale is carried through`() {
        val result = RepositoryResult(value = "cached", isStale = true)

        assertTrue(result.isStale)
        assertFalse(result.didFail)
    }

    @Test
    fun `a failure marks the result as failed`() {
        val failure = SdkError.NetworkError(statusCode = 500, message = "server exploded")
        val result = RepositoryResult(value = "cached", isStale = true, failure = failure)

        assertTrue(result.didFail)
        assertTrue(result.failure === failure)
        assertFalse(result.isOffline)
    }

    @Test
    fun `isOffline forwards from a no internet failure`() {
        val result = RepositoryResult(value = "cached", isStale = true, failure = SdkError.NoInternetConnection())

        assertTrue(result.didFail)
        assertTrue(result.isOffline)
    }

    @Test
    fun `isOffline is false for other failure types`() {
        val result = RepositoryResult(value = "cached", failure = SdkError.AuthenticationRequired())

        assertTrue(result.didFail)
        assertFalse(result.isOffline)
    }

    @Test
    fun `SdkError reports offline only for no internet connection`() {
        assertTrue(SdkError.NoInternetConnection().isOffline)
        assertFalse(SdkError.AuthenticationRequired().isOffline)
        assertFalse(SdkError.NetworkError(statusCode = 404, message = "missing").isOffline)
        assertFalse(SdkError.Unknown(IllegalStateException("boom")).isOffline)
    }

    @Test
    fun `a null value is a valid result payload`() {
        val result = RepositoryResult<String?>(value = null, isStale = true)

        assertNull(result.value)
        assertTrue(result.isStale)
        assertFalse(result.didFail)
    }
}
