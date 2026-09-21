package io.esimplified.sdk.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TokenRefreshTest {

    // region Which refusals end a session
    @Test
    fun `a 401 on the refresh is always the server rejecting the grant`() {
        assertTrue(TokenRefresh.isSessionRejection(401, null))
        assertTrue(TokenRefresh.isSessionRejection(401, "<html>whatever</html>"))
    }

    @Test
    fun `a 400 or 403 only rejects the grant when the body says so`() {
        assertTrue(TokenRefresh.isSessionRejection(400, """{"error":"invalid_grant"}"""))
        assertTrue(TokenRefresh.isSessionRejection(403, """{"error":"invalid_token"}"""))
        assertFalse(TokenRefresh.isSessionRejection(400, """{"detail":"malformed request"}"""))
        assertFalse(TokenRefresh.isSessionRejection(403, "<html>blocked by the edge</html>"))
        assertFalse(TokenRefresh.isSessionRejection(403, null))
    }

    @Test
    fun `a server or throttling failure never rejects the grant`() {
        listOf(429, 500, 502, 503, 504).forEach {
            assertFalse("HTTP $it must not end a session", TokenRefresh.isSessionRejection(it, """{"error":"invalid_grant"}"""))
        }
    }
    // endregion

    // region One refresh at a time
    @Test
    fun `a suspending refresh waits for a blocking one to finish`() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)

        val holder = Thread {
            TokenRefreshGate.withRefreshPermit {
                order += "blocking-in"
                holding.countDown()
                release.await(5, TimeUnit.SECONDS)
                order += "blocking-out"
            }
        }
        holder.start()
        assertTrue(holding.await(5, TimeUnit.SECONDS))

        val waiter = Thread {
            runBlocking {
                TokenRefreshGate.withRefreshPermitSuspending { order += "suspending-in" }
            }
        }
        waiter.start()
        Thread.sleep(100)
        release.countDown()
        holder.join(5_000)
        waiter.join(5_000)

        assertEquals(listOf("blocking-in", "blocking-out", "suspending-in"), order.toList())
    }

    @Test
    fun `a blocking refresh waits for a suspending one to finish`() {
        val order = Collections.synchronizedList(mutableListOf<String>())
        val holding = CountDownLatch(1)
        val release = CountDownLatch(1)

        val holder = Thread {
            runBlocking {
                TokenRefreshGate.withRefreshPermitSuspending {
                    order += "suspending-in"
                    holding.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    order += "suspending-out"
                }
            }
        }
        holder.start()
        assertTrue(holding.await(5, TimeUnit.SECONDS))

        val waiter = Thread {
            TokenRefreshGate.withRefreshPermit { order += "blocking-in" }
        }
        waiter.start()
        Thread.sleep(100)
        release.countDown()
        holder.join(5_000)
        waiter.join(5_000)

        assertEquals(listOf("suspending-in", "suspending-out", "blocking-in"), order.toList())
    }

    @Test
    fun `a failing refresh still hands the permit back`() {
        runCatching {
            TokenRefreshGate.withRefreshPermit { throw IllegalStateException("boom") }
        }

        val acquired = TokenRefreshGate.permit.tryAcquire(1, TimeUnit.SECONDS)
        if (acquired) TokenRefreshGate.permit.release()
        assertTrue("The permit was never released", acquired)
    }
    // endregion
}
