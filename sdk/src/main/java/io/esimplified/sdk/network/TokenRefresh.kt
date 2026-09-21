package io.esimplified.sdk.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.Semaphore

internal object TokenRefresh {

    private val GRANT_REJECTIONS = setOf("invalid_grant", "invalid_token")

    fun isSessionRejection(statusCode: Int, body: String?): Boolean = when (statusCode) {
        401 -> true
        400, 403 -> carriesGrantRejection(body)
        else -> false
    }

    fun carriesGrantRejection(body: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        return GRANT_REJECTIONS.any { body.contains(it, ignoreCase = true) }
    }
}

internal object TokenRefreshGate {

    val permit = Semaphore(1, true)

    inline fun <T> withRefreshPermit(block: () -> T): T {
        permit.acquire()
        try {
            return block()
        } finally {
            permit.release()
        }
    }

    suspend fun <T> withRefreshPermitSuspending(block: suspend () -> T): T {
        acquire()
        try {
            return block()
        } finally {
            permit.release()
        }
    }

    private suspend fun acquire() {
        var acquired = false
        try {
            runInterruptible(Dispatchers.IO) {
                permit.acquire()
                acquired = true
            }
        } catch (interruption: Throwable) {
            if (acquired) permit.release()
            throw interruption
        }
    }
}
