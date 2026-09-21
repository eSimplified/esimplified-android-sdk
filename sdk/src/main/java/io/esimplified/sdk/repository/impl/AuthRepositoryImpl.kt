package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.AuthRepository
import io.esimplified.sdk.repository.InvalidRefreshTokenException
import io.esimplified.sdk.repository.apiRead
import io.esimplified.sdk.repository.apiRejection
import io.esimplified.sdk.repository.asNetworkError

import io.esimplified.sdk.model.ChangePasswordResponse
import io.esimplified.sdk.model.CustomerChangePassword
import io.esimplified.sdk.model.CustomerDetails
import io.esimplified.sdk.model.CustomerForgetPassword
import io.esimplified.sdk.model.CustomerForgetPasswordResponse
import io.esimplified.sdk.model.ProfileResponse
import io.esimplified.sdk.model.UpdateCustomerPreferencesRequest
import io.esimplified.sdk.model.VerifyEmailRequest
import io.esimplified.sdk.model.VerifyEmailResponse
import io.esimplified.sdk.model.GetTokenResponse
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.auth.SecureStorageProvider
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import io.esimplified.sdk.SdkLog
import java.time.LocalDateTime

internal class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val secureStorage: SecureStorageProvider,
    private val cache: SdkCache
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "email"
        private const val UPDATE_FAILED_MESSAGE = "Update failed"
        private const val LOGIN_FAILED_MESSAGE = "Login failed"
        private const val GOOGLE_SIGN_IN_FAILED_MESSAGE = "Google sign-in failed"
        private const val EMAIL_VERIFICATION_FAILED_MESSAGE = "Email verification failed"
    }

    // region Authentication
    override suspend fun login(email: String, password: String): Customer {
        SdkLog.d("Login attempt")
        val response = apiService.getAuthToken(
            grantType = "password",
            username = email,
            password = password
        )

        SdkLog.d("Login response code: ${response.code()}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            SdkLog.e("Login failed [${response.code()}]")
            val message = try {
                val errorResponse = json.decodeFromString<GetTokenResponse>(errorBody)
                errorResponse.description ?: errorResponse.detail ?: errorResponse.error
            } catch (_: Exception) {
                null
            }
            throw apiRejection(message, LOGIN_FAILED_MESSAGE, response.code())
        }

        val body = response.body() ?: throw apiRejection(null, LOGIN_FAILED_MESSAGE)

        if (!body.error.isNullOrEmpty() || !body.detail.isNullOrEmpty()) {
            throw apiRejection(body.description ?: body.detail, LOGIN_FAILED_MESSAGE)
        }

        val user = body.user ?: throw apiRejection(null, LOGIN_FAILED_MESSAGE)
        val accessToken = body.accessToken ?: throw apiRejection(null, LOGIN_FAILED_MESSAGE)

        val auth = Auth.Authenticated(
            user = user,
            accessToken = accessToken,
            refreshToken = body.refreshToken ?: "",
            expires = calculateExpiration(body.expiresIn)
        )
        sessionManager.save(auth)

        SdkLog.d("Login successful")
        return user
    }

    override suspend fun loginWithRefreshToken(refreshToken: String): Customer {
        SdkLog.d("Refreshing token")
        val response = apiService.getAuthToken(
            grantType = "refresh_token",
            refreshToken = refreshToken
        )

        SdkLog.d("Refresh response code: ${response.code()}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            SdkLog.e("Token refresh failed [${response.code()}]")
            sessionManager.onAuthenticationFailed()
            throw InvalidRefreshTokenException()
        }

        val body = response.body() ?: run {
            sessionManager.onAuthenticationFailed()
            throw InvalidRefreshTokenException()
        }

        if (!body.error.isNullOrEmpty() || !body.detail.isNullOrEmpty()) {
            sessionManager.onAuthenticationFailed()
            throw InvalidRefreshTokenException()
        }

        val user = body.user ?: run {
            sessionManager.onAuthenticationFailed()
            throw InvalidRefreshTokenException()
        }
        val accessToken = body.accessToken ?: run {
            sessionManager.onAuthenticationFailed()
            throw InvalidRefreshTokenException()
        }

        val auth = Auth.Authenticated(
            user = user,
            accessToken = accessToken,
            refreshToken = body.refreshToken?.takeIf { it.isNotEmpty() } ?: refreshToken,
            expires = calculateExpiration(body.expiresIn)
        )
        sessionManager.save(auth)

        SdkLog.d("Token refresh successful")
        return user
    }

    override suspend fun signInWithGoogle(
        email: String,
        firstName: String,
        lastName: String,
        fullName: String,
        phoneNumber: String,
        providerAccountId: String,
        idToken: String
    ): Customer {
        try {
            val response = apiService.getSignInWith(
                email = email,
                firstName = firstName,
                lastName = lastName,
                provider = "google",
                providerAccountId = providerAccountId,
                fullName = fullName,
                phoneNumber = phoneNumber,
                grantType = "client_credentials",
                idToken = idToken,
                sender = "android"
            )

            if (!response.error.isNullOrEmpty() || !response.detail.isNullOrEmpty()) {
                throw apiRejection(response.description ?: response.detail, GOOGLE_SIGN_IN_FAILED_MESSAGE)
            }

            val user = response.user ?: throw apiRejection(null, GOOGLE_SIGN_IN_FAILED_MESSAGE)
            val accessToken = response.accessToken ?: throw apiRejection(null, GOOGLE_SIGN_IN_FAILED_MESSAGE)

            val auth = Auth.Authenticated(
                user = user,
                accessToken = accessToken,
                refreshToken = response.refreshToken ?: "",
                expires = calculateExpiration(response.expiresIn)
            )
            sessionManager.save(auth)

            return user
        } catch (e: HttpException) {
            throw e.asNetworkError(GOOGLE_SIGN_IN_FAILED_MESSAGE)
        }
    }
    // endregion

    // region Registration
    override suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phoneNumber: String,
        marketingConsent: Boolean?,
        referredBy: String?,
        loyaltyElection: String?
    ): ProfileResponse {
        try {
            val response = apiService.register(
                CustomerDetails(
                    email = email,
                    password = password,
                    firstName = firstName,
                    lastName = lastName,
                    fullName = "$firstName $lastName",
                    phoneNumber = phoneNumber,
                    marketingConsent = marketingConsent,
                    referredBy = referredBy,
                    loyaltyElection = loyaltyElection
                )
            )

            if (!response.detail.isNullOrEmpty()) {
                throw apiRejection(response.detail)
            }

            return response
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }
    }
    // endregion

    // region Password Management
    override suspend fun forgotPassword(email: String): CustomerForgetPasswordResponse {
        try {
            val response = apiService.forgetPassword(CustomerForgetPassword(email = email))

            if (!response.detail.isNullOrEmpty()) {
                throw apiRejection(response.detail)
            }

            return response
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String
    ): ChangePasswordResponse {
        val auth = sessionManager.getAuthState()
        val userId = if (auth is Auth.Authenticated) auth.user.id else ""

        try {
            val response = apiService.changePassword(
                CustomerChangePassword(id = userId, password = currentPassword, newPassword = newPassword)
            )
            if (!response.detail.isNullOrEmpty()) {
                throw apiRejection(response.detail)
            }
            return response
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }
    }

    override suspend fun resetPassword(
        email: String,
        token: String,
        newPassword: String
    ): ChangePasswordResponse {
        try {
            val response = apiService.changePassword(
                CustomerChangePassword(email = email, token = token, newPassword = newPassword)
            )
            if (!response.detail.isNullOrEmpty()) {
                throw apiRejection(response.detail)
            }
            return response
        } catch (e: HttpException) {
            throw e.asNetworkError()
        }
    }
    // endregion

    // region Email Verification
    override suspend fun verifyEmail(email: String, token: String, orderUUID: String?): VerifyEmailResponse {
        try {
            val response = apiService.verifyEmail(VerifyEmailRequest(email, token, orderUUID))
            if (!response.isVerified) {
                throw apiRejection(response.detail, EMAIL_VERIFICATION_FAILED_MESSAGE)
            }
            return response
        } catch (e: HttpException) {
            throw e.asNetworkError(EMAIL_VERIFICATION_FAILED_MESSAGE)
        }
    }
    // endregion

    // region Profile
    override suspend fun deleteProfile(): io.esimplified.sdk.model.DeleteProfileResponse =
        apiRead { apiService.deleteProfile() }
    // endregion

    // region User & Preferences
    override suspend fun getUser(): Customer? = fetchProfile()

    override suspend fun fetchProfile(): Customer? {
        if (sessionManager.getAuthState() !is Auth.Authenticated) {
            return null
        }
        val user = mergedWithSessionUser(withLoyaltyProvider(apiService.getUser()))
        saveUserOnCurrentSession(user)
        return user
    }

    private fun mergedWithSessionUser(user: Customer): Customer {
        val currentAuth = sessionManager.getAuthState()
        if (currentAuth !is Auth.Authenticated) {
            return user
        }
        return user.copy(
            referralCode = user.referralCode ?: currentAuth.user.referralCode
        )
    }

    private suspend fun refreshedProfileOrNull(): Customer? {
        return try {
            fetchProfile()
        } catch (e: Exception) {
            SdkLog.e("Failed to re-fetch the customer profile after a partial update", e)
            null
        }
    }

    private fun saveUserOnCurrentSession(user: Customer) {
        val currentAuth = sessionManager.getAuthState()
        if (currentAuth is Auth.Authenticated && currentAuth.user != user) {
            sessionManager.save(currentAuth.copy(user = user))
        }
    }

    private suspend fun withLoyaltyProvider(user: Customer): Customer {
        return try {
            val preferences = apiService.getCustomerPreferences()
            user.copy(
                loyaltyProvider = preferences.loyaltyProvider ?: user.loyaltyProvider,
                mokafaaEnrollment = preferences.mokafaaEnrollment ?: user.mokafaaEnrollment,
            )
        } catch (e: Exception) {
            SdkLog.e("Failed to fetch customer preferences for loyalty provider", e)
            user
        }
    }

    override suspend fun updatePreferences(
        preferredLanguage: String?,
        preferredCurrency: String?
    ): Customer {
        val response = apiService.updatePreferences(
            UpdateCustomerPreferencesRequest(
                preferredLanguage = preferredLanguage,
                preferredCurrency = preferredCurrency
            )
        )

        val refreshed = refreshedProfileOrNull()
        if (refreshed != null) {
            val reconciled = refreshed.copy(
                preferredLanguage = preferredLanguage ?: refreshed.preferredLanguage,
                preferredCurrency = preferredCurrency ?: refreshed.preferredCurrency
            )
            saveUserOnCurrentSession(reconciled)
            return reconciled
        }

        val snapshot = sessionManager.getAuthState()
        if (snapshot is Auth.Authenticated) {
            sessionManager.save(
                snapshot.copy(
                    user = snapshot.user.copy(
                        preferredLanguage = preferredLanguage,
                        preferredCurrency = preferredCurrency
                    )
                )
            )
        }

        return response
    }

    override suspend fun updateCustomerProfile(
        firstName: String?,
        lastName: String?,
        phoneNumber: String?,
        email: String?,
        password: String?,
    ): ProfileResponse {
        try {
            val userId = secureStorage.secureLoad(KEY_USER_ID, "")
            val userEmail = secureStorage.secureLoad(KEY_USER_EMAIL, "")
            val fullName = listOfNotNull(firstName, lastName)
                .joinToString(" ")
                .takeIf { it.isNotEmpty() }

            val response = apiService.update(
                CustomerDetails(
                    id = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    fullName = fullName,
                    phoneNumber = phoneNumber,
                    newEmail = email?.takeIf { it != userEmail },
                    password = password,
                )
            )

            if (response.detail != null) {
                throw apiRejection(response.detail, UPDATE_FAILED_MESSAGE)
            }

            if (response.success == false || response.updated == false) {
                throw apiRejection(response.detail ?: response.message, UPDATE_FAILED_MESSAGE)
            }

            val refreshed = refreshedProfileOrNull()
            if (refreshed != null) {
                saveUserOnCurrentSession(
                    refreshed.copy(
                        email = email ?: refreshed.email,
                        firstName = firstName ?: refreshed.firstName,
                        lastName = lastName ?: refreshed.lastName,
                        fullName = fullName ?: refreshed.fullName,
                        phoneNumber = phoneNumber ?: refreshed.phoneNumber,
                    )
                )
                return response
            }

            val snapshot = sessionManager.getAuthState()
            if (snapshot is Auth.Authenticated) {
                sessionManager.save(
                    snapshot.copy(
                        user = snapshot.user.copy(
                            email = email ?: snapshot.user.email,
                            firstName = firstName ?: snapshot.user.firstName,
                            lastName = lastName ?: snapshot.user.lastName,
                            fullName = fullName ?: snapshot.user.fullName,
                            phoneNumber = phoneNumber ?: snapshot.user.phoneNumber,
                        )
                    )
                )
            }

            return response
        } catch (e: HttpException) {
            throw e.asNetworkError(UPDATE_FAILED_MESSAGE)
        }
    }

    override suspend fun updateProfile(
        email: String,
        firstName: String?,
        lastName: String?,
        phoneNumber: String?,
        password: String
    ): ProfileResponse {
        try {
            val userId = secureStorage.secureLoad(KEY_USER_ID, "")
            val userEmail = secureStorage.secureLoad(KEY_USER_EMAIL, "")
            val fullName = listOfNotNull(firstName, lastName).joinToString(" ")

            val response = apiService.update(
                CustomerDetails(
                    id = userId,
                    email = email,
                    firstName = firstName,
                    lastName = lastName,
                    fullName = fullName,
                    phoneNumber = phoneNumber,
                    newEmail = if (userEmail == email) null else email,
                    password = password,
                )
            )

            if (response.detail != null) {
                throw apiRejection(response.detail, UPDATE_FAILED_MESSAGE)
            }

            if (response.success == false || response.updated == false) {
                throw apiRejection(response.detail ?: response.message, UPDATE_FAILED_MESSAGE)
            }

            val snapshot = sessionManager.getAuthState()
            if (snapshot is Auth.Authenticated) {
                sessionManager.save(
                    snapshot.copy(
                        user = snapshot.user.copy(
                            email = email,
                            firstName = firstName,
                            lastName = lastName,
                            fullName = fullName,
                            phoneNumber = phoneNumber,
                        )
                    )
                )
            }

            return response
        } catch (e: HttpException) {
            throw e.asNetworkError(UPDATE_FAILED_MESSAGE)
        }
    }
    // endregion

    // region Session
    override suspend fun logout() {
        sessionManager.save(Auth.Unauthenticated)
        cache.clear()
        SdkLog.d("Logged out and cleared all cached responses")
    }
    // endregion

    // region Private Helpers
    private fun calculateExpiration(expiresIn: Int): LocalDateTime {
        return LocalDateTime.now().plusSeconds(expiresIn.toLong())
    }

    // endregion
}
