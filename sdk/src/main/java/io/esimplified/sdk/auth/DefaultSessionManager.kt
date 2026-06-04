package io.esimplified.sdk.auth

import io.esimplified.sdk.model.Customer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.time.LocalDateTime

internal class DefaultSessionManager(
    private val storage: SecureStorageProvider
) : SessionManager {

    companion object {
        // Same key constants as the app's SessionService so existing logged-in users stay logged in
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_EMAIL = "email"
        const val KEY_USER_FIRST_NAME = "first_name"
        const val KEY_USER_LAST_NAME = "last_name"
        const val KEY_USER_FULL_NAME = "full_name"
        const val KEY_USER_PHONE_NUMBER = "phone_number"
        const val KEY_USER_REFERRAL_CODE = "referral_code"
        const val KEY_USER_EXTERNAL_REFERENCE = "external_reference"
        const val KEY_USER_PREFERRED_LANGUAGE = "preferred_language"
        const val KEY_USER_PREFERRED_CURRENCY = "preferred_currency"

        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_ACCESS_TOKEN_EXPIRE = "refresh_token_expiration"
        const val AUTH_GRANT_TYPE = "password"
        const val AUTH_GRANT_TYPE_REFRESH_TOKEN = "refresh_token"
        const val AUTH_GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials"
    }

    private fun getStorageState(): Auth {
        return try {
            val accessToken = storage.secureLoad(KEY_ACCESS_TOKEN, "")
            val refreshToken = storage.secureLoad(KEY_REFRESH_TOKEN, "")
            val expiresIn = storage.secureLoad(KEY_ACCESS_TOKEN_EXPIRE, "")

            val user = Customer(
                id = storage.secureLoad(KEY_USER_ID, ""),
                firstName = storage.secureLoad(KEY_USER_FIRST_NAME, ""),
                lastName = storage.secureLoad(KEY_USER_LAST_NAME, ""),
                fullName = storage.secureLoad(KEY_USER_FULL_NAME, ""),
                phoneNumber = storage.secureLoad(KEY_USER_PHONE_NUMBER, ""),
                externalReference = storage.secureLoad(KEY_USER_EXTERNAL_REFERENCE, ""),
                referralCode = storage.secureLoad(KEY_USER_REFERRAL_CODE, ""),
                email = storage.secureLoad(KEY_USER_EMAIL, ""),
                preferredLanguage = storage.secureLoad(KEY_USER_PREFERRED_LANGUAGE, "").takeIf { it.isNotEmpty() },
                preferredCurrency = storage.secureLoad(KEY_USER_PREFERRED_CURRENCY, "").takeIf { it.isNotEmpty() }
            )

            if (accessToken.isEmpty() || user.id.isEmpty()) {
                Auth.Unauthenticated
            } else {
                Auth.Authenticated(
                    user = user,
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expires = LocalDateTime.parse(expiresIn)
                )
            }
        } catch (e: Exception) {
            Timber.d(e, "Failed to restore session from storage")
            Auth.Unauthenticated
        }
    }

    private val _state = MutableStateFlow(getStorageState())
    val state: StateFlow<Auth> = _state.asStateFlow()

    fun refreshFromStorage() {
        _state.update { getStorageState() }
    }

    override fun save(auth: Auth) {
        when (auth) {
            is Auth.Authenticated -> {
                mapOf(
                    KEY_USER_ID to auth.user.id,
                    KEY_USER_EMAIL to auth.user.email,
                    KEY_USER_FIRST_NAME to auth.user.firstName,
                    KEY_USER_LAST_NAME to auth.user.lastName,
                    KEY_USER_FULL_NAME to auth.user.fullName,
                    KEY_USER_PHONE_NUMBER to auth.user.phoneNumber,
                    KEY_USER_REFERRAL_CODE to auth.user.referralCode,
                    KEY_USER_EXTERNAL_REFERENCE to auth.user.externalReference,
                    KEY_USER_PREFERRED_LANGUAGE to (auth.user.preferredLanguage ?: ""),
                    KEY_USER_PREFERRED_CURRENCY to (auth.user.preferredCurrency ?: ""),
                    KEY_ACCESS_TOKEN to auth.accessToken,
                    KEY_REFRESH_TOKEN to auth.refreshToken,
                    KEY_ACCESS_TOKEN_EXPIRE to auth.expires.toString()
                ).forEach { entry ->
                    storage.secureSave(entry.value.orEmpty(), entry.key)
                }
            }

            Auth.Unauthenticated -> {
                storage.clearSecureStorage()
            }
        }

        _state.update { auth }
    }

    override fun isAuthenticated(): Boolean {
        return state.value is Auth.Authenticated
    }

    override fun getAccessToken(): String {
        return (state.value as? Auth.Authenticated)?.accessToken.orEmpty()
    }

    override fun getRefreshToken(): String {
        return (state.value as? Auth.Authenticated)?.refreshToken.orEmpty()
    }

    override fun getAuthState(): Auth {
        return state.value
    }

    override fun onAuthenticationFailed() {
        save(Auth.Unauthenticated)
    }
}
