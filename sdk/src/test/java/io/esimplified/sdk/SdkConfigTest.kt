package io.esimplified.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SdkConfigTest {

    @Test
    fun `default values are correct`() {
        val config = SdkConfig(
            baseUrl = "https://api.example.com",
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
            baseUrl = "https://custom.api.com",
            apiVersion = "v3",
            clientId = "custom-id",
            clientSecret = "custom-secret",
            awsWafToken = "waf-token-123",
            enableLogging = true
        )

        assertEquals("https://custom.api.com", config.baseUrl)
        assertEquals("v3", config.apiVersion)
        assertEquals("custom-id", config.clientId)
        assertEquals("custom-secret", config.clientSecret)
        assertEquals("waf-token-123", config.awsWafToken)
        assertEquals(true, config.enableLogging)
    }
}
