package io.esimplified.sdk.network

import io.esimplified.sdk.SdkConfig
import io.esimplified.sdk.SdkLog
import io.esimplified.sdk.SdkLogLevel
import io.esimplified.sdk.SdkLogger
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SdkAuthInterceptorRefreshFailureTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var sessionManager: DefaultSessionManager
    private lateinit var client: OkHttpClient
    private lateinit var logger: RecordingLogger

    @Before
    fun setup() {
        logger = RecordingLogger()
        SdkLog.delegate = logger
        mockWebServer = MockWebServer()
        mockWebServer.start()
        sessionManager = DefaultSessionManager(FakeSecureStorage())
        val config = SdkConfig.forTesting(
            baseUrl = mockWebServer.url("/").toString().trimEnd('/'),
            clientId = "test-client",
            clientSecret = "test-secret",
        )
        client = OkHttpClient.Builder()
            .addInterceptor(SdkAuthInterceptor(sessionManager, config))
            .build()
    }

    @After
    fun teardown() {
        SdkLog.resetForTesting()
        mockWebServer.shutdown()
    }

    // region The token response never leaves the device
    @Test
    fun `an unparseable token response never reaches the log or the thrown error`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"access_token":{"unexpected":true},"refresh_token":"$LEAKED_SECRET"}"""
            )
        )

        val thrown = execute()

        assertNotNull("Expected the call to surface an error", thrown)
        assertFalse(
            "The token body must not reach the exception message: ${describe(thrown)}",
            describe(thrown).contains(LEAKED_SECRET)
        )
        assertFalse(
            "The token body must not reach the log: ${logger.rendered()}",
            logger.rendered().contains(LEAKED_SECRET)
        )
        assertTrue(
            "An unparseable refresh response is not the server rejecting the session",
            sessionManager.getAuthState() is Auth.Authenticated
        )
    }

    private fun describe(error: Throwable?): String {
        val parts = mutableListOf<String>()
        var current = error
        while (current != null) {
            parts += current.toString()
            current = current.cause
        }
        return parts.joinToString(" | ")
    }
    // endregion

    // region A refresh the server did not reject keeps the session
    @Test
    fun `a 500 on the reactive refresh keeps the session and surfaces the failure`() {
        assertSurvivesReactiveRefresh(statusCode = 500)
    }

    @Test
    fun `a 429 on the reactive refresh keeps the session and surfaces the failure`() {
        assertSurvivesReactiveRefresh(statusCode = 429)
    }

    @Test
    fun `a 502 on the reactive refresh keeps the session and surfaces the failure`() {
        assertSurvivesReactiveRefresh(statusCode = 502)
    }

    @Test
    fun `a 503 on the proactive refresh keeps the session and surfaces the failure`() {
        assertSurvivesProactiveRefresh(statusCode = 503)
    }

    @Test
    fun `a 429 on the proactive refresh keeps the session and surfaces the failure`() {
        assertSurvivesProactiveRefresh(statusCode = 429)
    }

    @Test
    fun `a refusal the server did not author carries its status code`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(503).setBody("""{"detail":"upstream down"}"""))

        val thrown = execute()

        assertTrue("Expected a NetworkError, got $thrown", thrown is SdkError.NetworkError)
        assertEquals(503, (thrown as SdkError.NetworkError).statusCode)
        assertTrue(thrown.message!!.contains("upstream down"))
    }
    // endregion

    // region A refresh the server did reject ends the session
    @Test
    fun `a 400 on the refresh ends the session`() {
        assertEndsSession(statusCode = 400)
    }

    @Test
    fun `a 401 on the refresh ends the session`() {
        assertEndsSession(statusCode = 401)
    }

    @Test
    fun `a 403 on the refresh ends the session`() {
        assertEndsSession(statusCode = 403)
    }

    @Test
    fun `a 403 the oauth server did not author keeps the session`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(
            MockResponse().setResponseCode(403).setBody("<html><body>Request blocked</body></html>")
        )

        val thrown = execute()

        assertFalse(
            "A 403 without a grant rejection is the edge refusing us, not the session ending",
            thrown is SdkError.AuthenticationRequired
        )
        assertTrue(sessionManager.getAuthState() is Auth.Authenticated)
    }

    @Test
    fun `a 400 without a grant rejection keeps the session`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"malformed request"}"""))

        val thrown = execute()

        assertFalse(thrown is SdkError.AuthenticationRequired)
        assertTrue(sessionManager.getAuthState() is Auth.Authenticated)
    }

    @Test
    fun `a 403 on a normal call is not a refresh trigger`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(403).setBody("<html>blocked</html>"))

        val code = client.newCall(Request.Builder().url(mockWebServer.url("/api/test")).build())
            .execute()
            .use { it.code }

        assertEquals(403, code)
        assertEquals("A 403 must not send the SDK to the token endpoint", 1, mockWebServer.requestCount)
        assertTrue(sessionManager.getAuthState() is Auth.Authenticated)
    }

    @Test
    fun `a refresh that carries no access token keeps the session`() {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"expires_in":3600}"""))

        val thrown = execute()

        assertFalse(thrown is SdkError.AuthenticationRequired)
        val state = sessionManager.getAuthState()
        assertTrue(state is Auth.Authenticated)
        assertEquals("tok", (state as Auth.Authenticated).accessToken)
    }

    @Test
    fun `an empty refresh token ends the session without a network call`() {
        sessionManager.save(authenticated(refreshToken = ""))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        val thrown = execute()

        assertTrue(thrown is SdkError.AuthenticationRequired)
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
        assertEquals(1, mockWebServer.requestCount)
    }
    // endregion

    // region Concurrency
    @Test
    fun `concurrent rejected requests refresh once and all retry with the new token`() {
        sessionManager.save(authenticated(accessToken = STALE_TOKEN))
        val refreshes = AtomicInteger(0)
        mockWebServer.dispatcher = refreshingDispatcher(refreshes)

        val outcomes = executeConcurrently(threads = 8)

        assertEquals("The refresh must be coalesced", 1, refreshes.get())
        assertEquals(8, outcomes.size)
        assertTrue("Every retried call should have succeeded: $outcomes", outcomes.all { it == 200 })
    }

    @Test
    fun `concurrent refreshes do not write a stale refresh token over the new one`() {
        sessionManager.save(authenticated(accessToken = STALE_TOKEN))
        mockWebServer.dispatcher = refreshingDispatcher(AtomicInteger(0))

        executeConcurrently(threads = 8)

        val state = sessionManager.getAuthState() as Auth.Authenticated
        assertEquals(FRESH_TOKEN, state.accessToken)
        assertEquals(FRESH_REFRESH_TOKEN, state.refreshToken)
    }

    @Test
    fun `a concurrent refresh keeps the customer record the newest save left behind`() {
        sessionManager.save(authenticated(accessToken = STALE_TOKEN))
        mockWebServer.dispatcher = refreshingDispatcher(AtomicInteger(0))

        executeConcurrently(threads = 4)

        val state = sessionManager.getAuthState() as Auth.Authenticated
        assertEquals("u@e.com", state.user.email)
    }

    @Test
    fun `a proactive refresh under load only goes to the server once`() {
        sessionManager.save(
            authenticated(accessToken = STALE_TOKEN).copy(expires = LocalDateTime.now().plusMinutes(1))
        )
        val refreshes = AtomicInteger(0)
        mockWebServer.dispatcher = refreshingDispatcher(refreshes)

        val outcomes = executeConcurrently(threads = 8)

        assertEquals("The proactive refresh must be coalesced", 1, refreshes.get())
        assertTrue(outcomes.all { it == 200 })
    }
    // endregion

    private fun assertSurvivesReactiveRefresh(statusCode: Int) {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(statusCode))

        val thrown = execute()

        assertNotNull("Expected the call to surface an error", thrown)
        assertFalse(
            "HTTP $statusCode on a refresh is not the server rejecting the session",
            thrown is SdkError.AuthenticationRequired
        )
        assertTrue(
            "HTTP $statusCode on a refresh must not log the user out",
            sessionManager.getAuthState() is Auth.Authenticated
        )
    }

    private fun assertSurvivesProactiveRefresh(statusCode: Int) {
        sessionManager.save(authenticated().copy(expires = LocalDateTime.now().plusMinutes(1)))
        mockWebServer.enqueue(MockResponse().setResponseCode(statusCode))

        val thrown = execute()

        assertNotNull("Expected the call to surface an error", thrown)
        assertFalse(
            "HTTP $statusCode on a refresh is not the server rejecting the session",
            thrown is SdkError.AuthenticationRequired
        )
        assertTrue(
            "HTTP $statusCode on a refresh must not log the user out",
            sessionManager.getAuthState() is Auth.Authenticated
        )
    }

    private fun assertEndsSession(statusCode: Int) {
        sessionManager.save(authenticated())
        mockWebServer.enqueue(MockResponse().setResponseCode(401))
        mockWebServer.enqueue(MockResponse().setResponseCode(statusCode).setBody("""{"error":"invalid_grant"}"""))

        val thrown = execute()

        assertTrue("Expected AuthenticationRequired, got $thrown", thrown is SdkError.AuthenticationRequired)
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
    }

    private fun execute(): Throwable? = try {
        client.newCall(Request.Builder().url(mockWebServer.url("/api/test")).build()).execute().close()
        null
    } catch (error: IOException) {
        error
    }

    private fun executeConcurrently(threads: Int): List<Int> {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val codes = ConcurrentLinkedQueue<Int>()
        repeat(threads) {
            pool.submit {
                start.await()
                runCatching {
                    client.newCall(Request.Builder().url(mockWebServer.url("/api/test")).build())
                        .execute()
                        .use { codes.add(it.code) }
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue("Concurrent calls did not finish", pool.awaitTermination(30, TimeUnit.SECONDS))
        return codes.toList()
    }

    private fun refreshingDispatcher(refreshes: AtomicInteger) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path!!.contains("auth/token")) {
                refreshes.incrementAndGet()
                return MockResponse().setResponseCode(200).setBody(
                    """
                    {"access_token":"$FRESH_TOKEN","refresh_token":"$FRESH_REFRESH_TOKEN",
                     "expires_in":3600,"token_type":"Bearer"}
                    """.trimIndent()
                )
            }
            val authorization = request.getHeader("authorization")
            return if (authorization == "Bearer $FRESH_TOKEN") {
                MockResponse().setResponseCode(200).setBody("{}")
            } else {
                MockResponse().setResponseCode(401)
            }
        }
    }

    private fun authenticated(
        accessToken: String = "tok",
        refreshToken: String = "ref",
    ) = Auth.Authenticated(
        user = Customer(id = "u-1", email = "u@e.com"),
        accessToken = accessToken,
        refreshToken = refreshToken,
        expires = LocalDateTime.now().plusHours(1),
    )

    private class RecordingLogger : SdkLogger {
        private val lines = mutableListOf<String>()

        override fun log(level: SdkLogLevel, message: String, throwable: Throwable?) {
            synchronized(lines) { lines += "$message ${describeThrowable(throwable)}" }
        }

        fun rendered(): String = synchronized(lines) { lines.joinToString("\n") }

        private fun describeThrowable(throwable: Throwable?): String {
            var current = throwable
            val parts = mutableListOf<String>()
            while (current != null) {
                parts += current.toString()
                current = current.cause
            }
            return parts.joinToString(" | ")
        }
    }

    private companion object {
        const val STALE_TOKEN = "stale-token"
        const val LEAKED_SECRET = "rotated-refresh-token-that-must-never-be-logged"
        const val FRESH_TOKEN = "fresh-token"
        const val FRESH_REFRESH_TOKEN = "fresh-refresh-token"
    }
}
