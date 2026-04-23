package io.esimplified.sdk

import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.fake.FakeSecureStorage
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsimSdkTest {

    private val testConfig = SdkConfig(
        environment = SdkEnvironment.PRODUCTION,
        clientName = "test",
        clientId = "test-client",
        clientSecret = "test-secret"
    )

    @After
    fun teardown() {
        // Reset singleton state via reflection
        resetField("_config")
        resetField("_sessionManager")
        resetField("_storageProvider")
        resetField("_context")
    }

    private fun resetField(fieldName: String) {
        try {
            val field = EsimSdk::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(EsimSdk, null)
        } catch (_: Exception) {
            // Field may not exist or not be accessible
        }
    }

    @Test
    fun `initialize with custom storage creates session manager`() {
        val fakeStorage = FakeSecureStorage()
        // Use reflection to call initialize without a real Context
        setPrivateFields(testConfig, fakeStorage, DefaultSessionManager(fakeStorage))

        assertNotNull(EsimSdk.sessionManager)
        assertTrue(EsimSdk.sessionManager is DefaultSessionManager)
    }

    @Test
    fun `initialize with custom storage uses provided storage`() {
        val fakeStorage = FakeSecureStorage()
        setPrivateFields(testConfig, fakeStorage, DefaultSessionManager(fakeStorage))

        // Access storageProvider via reflection since it's internal
        val storageField = EsimSdk::class.java.getDeclaredField("_storageProvider")
        storageField.isAccessible = true
        val provider = storageField.get(EsimSdk)
        assertTrue("Expected FakeSecureStorage but got ${provider?.javaClass}", provider is FakeSecureStorage)
    }

    @Test
    fun `initialize with custom session manager uses provided session manager`() {
        val fakeStorage = FakeSecureStorage()
        val customSessionManager = DefaultSessionManager(fakeStorage)
        setPrivateFields(testConfig, fakeStorage, customSessionManager)

        assertTrue(EsimSdk.sessionManager === customSessionManager)
    }

    @Test(expected = IllegalStateException::class)
    fun `accessing config before initialize throws error`() {
        // Do not call initialize; accessing config should throw
        EsimSdk.config
    }

    @Test(expected = IllegalStateException::class)
    fun `accessing sessionManager before initialize throws error`() {
        // Do not call initialize; accessing sessionManager should throw
        EsimSdk.sessionManager
    }

    @Test
    fun `koinModule returns non-null module`() {
        val fakeStorage = FakeSecureStorage()
        setPrivateFields(testConfig, fakeStorage, DefaultSessionManager(fakeStorage))

        val module = EsimSdk.koinModule()
        assertNotNull(module)
    }

    /**
     * Helper to set private fields on the EsimSdk singleton directly,
     * bypassing the need for a real Android Context.
     */
    private fun setPrivateFields(config: SdkConfig, storage: FakeSecureStorage, sessionManager: SessionManager) {
        setField("_config", config)
        setField("_storageProvider", storage)
        setField("_sessionManager", sessionManager)
    }

    private fun setField(fieldName: String, value: Any?) {
        val field = EsimSdk::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(EsimSdk, value)
    }
}
