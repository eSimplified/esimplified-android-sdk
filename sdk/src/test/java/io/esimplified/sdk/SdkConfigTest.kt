package io.esimplified.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkConfigTest {

    @Test
    fun `default values are correct`() {
        val config = SdkConfig(
            environment = SdkEnvironment.PRODUCTION,
            clientName = "acme",
            clientId = "id",
            clientSecret = "secret"
        )

        assertEquals("v2", config.apiVersion)
        assertFalse(config.enableLogging)
        assertEquals("", config.awsWafToken)
    }

    @Test
    fun `custom values are preserved`() {
        val config = SdkConfig(
            environment = SdkEnvironment.STAGING,
            clientName = "acme",
            apiVersion = "v3",
            clientId = "custom-id",
            clientSecret = "custom-secret",
            awsWafToken = "waf-token-123",
            enableLogging = true
        )

        assertEquals(SdkEnvironment.STAGING, config.environment)
        assertEquals("acme", config.clientName)
        assertEquals("v3", config.apiVersion)
        assertEquals("custom-id", config.clientId)
        assertEquals("custom-secret", config.clientSecret)
        assertEquals("waf-token-123", config.awsWafToken)
        assertTrue(config.enableLogging)
    }

    @Test
    fun `production environment resolves correct base url`() {
        val config = SdkConfig(
            environment = SdkEnvironment.PRODUCTION,
            clientName = "knowroaming",
            clientId = "id",
            clientSecret = "secret"
        )
        assertEquals("https://knowroaming.live.esimplified.io", config.baseUrl)
    }

    @Test
    fun `staging environment resolves correct base url`() {
        val config = SdkConfig(
            environment = SdkEnvironment.STAGING,
            clientName = "knowroaming",
            clientId = "id",
            clientSecret = "secret"
        )
        assertEquals("https://knowroaming.stage.esimplified.io", config.baseUrl)
    }

    @Test
    fun `different client names produce different base urls`() {
        val config = SdkConfig(
            environment = SdkEnvironment.PRODUCTION,
            clientName = "duckesims",
            clientId = "id",
            clientSecret = "secret"
        )
        assertEquals("https://duckesims.live.esimplified.io", config.baseUrl)
    }
}
