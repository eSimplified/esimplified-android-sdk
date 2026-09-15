package io.esimplified.sdk

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkLogTest {

    @After
    fun teardown() {
        SdkLog.resetForTesting()
    }

    private class RecordingLogger : SdkLogger {
        val lines = mutableListOf<Triple<SdkLogLevel, String, Throwable?>>()
        override fun log(level: SdkLogLevel, message: String, throwable: Throwable?) {
            lines += Triple(level, message, throwable)
        }
    }

    // region Delegation
    @Test
    fun `a host supplied logger receives every level`() {
        val logger = RecordingLogger()
        SdkLog.delegate = logger

        SdkLog.d("debug")
        SdkLog.w("warning")
        SdkLog.e("error", IllegalStateException("boom"))

        assertEquals(3, logger.lines.size)
        assertEquals(SdkLogLevel.DEBUG, logger.lines[0].first)
        assertEquals(SdkLogLevel.WARNING, logger.lines[1].first)
        assertEquals(SdkLogLevel.ERROR, logger.lines[2].first)
        assertEquals("boom", logger.lines[2].third?.message)
    }

    @Test
    fun `a host supplied logger receives lines even when platform logging is off`() {
        val logger = RecordingLogger()
        SdkLog.delegate = logger
        SdkLog.isEnabled = false

        SdkLog.d("still delivered")

        assertEquals(1, logger.lines.size)
    }

    @Test
    fun `no logger and no enabled flag writes nowhere`() {
        SdkLog.resetForTesting()

        SdkLog.d("dropped")
        SdkLog.e("dropped", IllegalStateException("boom"))

        assertEquals(null, SdkLog.delegate)
    }
    // endregion

    // region Path redaction
    @Test
    fun `an iccid path segment is redacted`() {
        assertEquals(
            "/api/v2/customer/esims/…/details/",
            "/api/v2/customer/esims/8944500012345678901/details/".redactedPath(),
        )
    }

    @Test
    fun `an order uuid path segment is redacted`() {
        val redacted = "/api/v2/orders/3f2a91c4-77b5-4e21-9f1e-8d4c2b6a0e77/".redactedPath()

        assertEquals("/api/v2/orders/…/", redacted)
    }

    @Test
    fun `short readable segments survive redaction`() {
        val redacted = "/api/v2/customer/preferences/".redactedPath()

        assertEquals("/api/v2/customer/preferences/", redacted)
        assertTrue(redacted.contains("v2"))
    }
    // endregion
}
