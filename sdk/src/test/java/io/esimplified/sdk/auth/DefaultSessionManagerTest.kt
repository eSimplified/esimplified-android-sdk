package io.esimplified.sdk.auth

import io.esimplified.sdk.fake.FakeSecureStorage
import io.esimplified.sdk.model.Customer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime

class DefaultSessionManagerTest {

    private lateinit var fakeStorage: FakeSecureStorage
    private lateinit var sessionManager: DefaultSessionManager

    @Before
    fun setup() {
        fakeStorage = FakeSecureStorage()
        sessionManager = DefaultSessionManager(fakeStorage)
    }

    private fun createTestAuth(
        userId: String = "user-123",
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        email: String = "test@example.com",
        firstName: String = "John",
        lastName: String = "Doe",
        preferredCurrency: String? = null,
        preferredLanguage: String? = null
    ): Auth.Authenticated {
        return Auth.Authenticated(
            user = Customer(
                id = userId,
                email = email,
                firstName = firstName,
                lastName = lastName,
                fullName = "$firstName $lastName",
                phoneNumber = "+1234567890",
                externalReference = "ext-ref",
                referralCode = "REF123",
                preferredCurrency = preferredCurrency,
                preferredLanguage = preferredLanguage
            ),
            accessToken = accessToken,
            refreshToken = refreshToken,
            expires = LocalDateTime.of(2026, 12, 31, 23, 59, 59)
        )
    }

    @Test
    fun `initial state is Unauthenticated when storage is empty`() {
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)
    }

    @Test
    fun `save Authenticated state updates state flow`() {
        val auth = createTestAuth()
        sessionManager.save(auth)

        val state = sessionManager.state.value
        assertTrue(state is Auth.Authenticated)
        assertEquals("access-token", (state as Auth.Authenticated).accessToken)
    }

    @Test
    fun `save Authenticated state persists to storage`() {
        val auth = createTestAuth()
        sessionManager.save(auth)

        assertEquals("access-token", fakeStorage.secureLoad(DefaultSessionManager.KEY_ACCESS_TOKEN, ""))
        assertEquals("refresh-token", fakeStorage.secureLoad(DefaultSessionManager.KEY_REFRESH_TOKEN, ""))
        assertEquals("user-123", fakeStorage.secureLoad(DefaultSessionManager.KEY_USER_ID, ""))
        assertEquals("test@example.com", fakeStorage.secureLoad(DefaultSessionManager.KEY_USER_EMAIL, ""))
        assertEquals("John", fakeStorage.secureLoad(DefaultSessionManager.KEY_USER_FIRST_NAME, ""))
        assertEquals("Doe", fakeStorage.secureLoad(DefaultSessionManager.KEY_USER_LAST_NAME, ""))
    }

    @Test
    fun `getAccessToken returns stored token`() {
        val auth = createTestAuth(accessToken = "my-access-token")
        sessionManager.save(auth)

        assertEquals("my-access-token", sessionManager.getAccessToken())
    }

    @Test
    fun `getRefreshToken returns stored token`() {
        val auth = createTestAuth(refreshToken = "my-refresh-token")
        sessionManager.save(auth)

        assertEquals("my-refresh-token", sessionManager.getRefreshToken())
    }

    @Test
    fun `isAuthenticated returns true when authenticated`() {
        sessionManager.save(createTestAuth())
        assertTrue(sessionManager.isAuthenticated())
    }

    @Test
    fun `isAuthenticated returns false when unauthenticated`() {
        assertFalse(sessionManager.isAuthenticated())
    }

    @Test
    fun `save Unauthenticated clears tokens from storage`() {
        sessionManager.save(createTestAuth())
        assertTrue(sessionManager.isAuthenticated())

        sessionManager.save(Auth.Unauthenticated)

        assertFalse(sessionManager.isAuthenticated())
        assertEquals("", fakeStorage.secureLoad(DefaultSessionManager.KEY_ACCESS_TOKEN, ""))
        assertEquals("", fakeStorage.secureLoad(DefaultSessionManager.KEY_REFRESH_TOKEN, ""))
        assertEquals("", fakeStorage.secureLoad(DefaultSessionManager.KEY_USER_ID, ""))
    }

    @Test
    fun `getAuthState returns current state`() {
        assertTrue(sessionManager.getAuthState() is Auth.Unauthenticated)

        val auth = createTestAuth()
        sessionManager.save(auth)

        val state = sessionManager.getAuthState()
        assertTrue(state is Auth.Authenticated)
        assertEquals("user-123", (state as Auth.Authenticated).user.id)
    }

    @Test
    fun `loading from storage with existing tokens restores Authenticated state`() {
        // Pre-populate storage as if a previous session was saved
        fakeStorage.secureSave("stored-access-token", DefaultSessionManager.KEY_ACCESS_TOKEN)
        fakeStorage.secureSave("stored-refresh-token", DefaultSessionManager.KEY_REFRESH_TOKEN)
        fakeStorage.secureSave("user-456", DefaultSessionManager.KEY_USER_ID)
        fakeStorage.secureSave("stored@example.com", DefaultSessionManager.KEY_USER_EMAIL)
        fakeStorage.secureSave("Jane", DefaultSessionManager.KEY_USER_FIRST_NAME)
        fakeStorage.secureSave("Smith", DefaultSessionManager.KEY_USER_LAST_NAME)
        fakeStorage.secureSave("Jane Smith", DefaultSessionManager.KEY_USER_FULL_NAME)
        fakeStorage.secureSave("+9876543210", DefaultSessionManager.KEY_USER_PHONE_NUMBER)
        fakeStorage.secureSave("ext-456", DefaultSessionManager.KEY_USER_EXTERNAL_REFERENCE)
        fakeStorage.secureSave("REF456", DefaultSessionManager.KEY_USER_REFERRAL_CODE)
        fakeStorage.secureSave("en", DefaultSessionManager.KEY_USER_PREFERRED_LANGUAGE)
        fakeStorage.secureSave("USD", DefaultSessionManager.KEY_USER_PREFERRED_CURRENCY)
        fakeStorage.secureSave(LocalDateTime.of(2026, 12, 31, 23, 59, 59).toString(), DefaultSessionManager.KEY_ACCESS_TOKEN_EXPIRE)

        // Create a new session manager that should load from the pre-populated storage
        val restoredManager = DefaultSessionManager(fakeStorage)

        assertTrue(restoredManager.isAuthenticated())
        val state = restoredManager.getAuthState() as Auth.Authenticated
        assertEquals("stored-access-token", state.accessToken)
        assertEquals("stored-refresh-token", state.refreshToken)
        assertEquals("user-456", state.user.id)
        assertEquals("stored@example.com", state.user.email)
        assertEquals("Jane", state.user.firstName)
        assertEquals("Smith", state.user.lastName)
    }
}
