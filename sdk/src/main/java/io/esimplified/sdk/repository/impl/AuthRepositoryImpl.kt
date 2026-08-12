package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.AuthRepository
import io.esimplified.sdk.repository.InvalidRefreshTokenException

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
import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.auth.SecureStorageProvider
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import timber.log.Timber
import java.time.LocalDateTime

internal class AuthRepositoryImpl(
    private val apiService: ApiService,
    private val sessionManager: SessionManager,
    private val secureStorage: SecureStorageProvider
) : AuthRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "email"
        const val REFRESH_DIAGNOSTIC_TAG = "RefreshTokenDiagnostic"
    }

    // region Authentication
    override suspend fun login(email: String, password: String): Customer {
        Timber.d("Login attempt for: $email")
        val response = apiService.getAuthToken(
            grantType = "password",
            username = email,
            password = password
        )

        Timber.d("Login response code: ${response.code()}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            Timber.e("Login failed [${response.code()}]: $errorBody")
            val message = try {
                val errorResponse = json.decodeFromString<GetTokenResponse>(errorBody)
                errorResponse.description ?: errorResponse.detail ?: errorResponse.error
            } catch (_: Exception) {
                null
            }
            throw Exception(message ?: "Login failed (${response.code()})")
        }

        val body = response.body() ?: throw Exception("Login failed: empty response")

        if (!body.error.isNullOrEmpty() || !body.detail.isNullOrEmpty()) {
            throw Exception(body.description ?: body.detail ?: "Login failed")
        }

        val user = body.user ?: throw Exception("Login failed")
        val accessToken = body.accessToken ?: throw Exception("Login failed")

        val auth = Auth.Authenticated(
            user = user,
            accessToken = accessToken,
            refreshToken = body.refreshToken ?: "",
            expires = calculateExpiration(body.expiresIn)
        )
        sessionManager.save(auth)

        val enrichedUser = withLoyaltyProvider(user)
        saveUserOnCurrentSession(enrichedUser)

        Timber.d("Login successful for: ${user.email}")
        return enrichedUser
    }

    override suspend fun loginWithRefreshToken(refreshToken: String): Customer {
        Timber.d("Refreshing token")
        val response = apiService.getAuthToken(
            grantType = "refresh_token",
            refreshToken = refreshToken
        )

        Timber.d("Refresh response code: ${response.code()}")

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string().orEmpty()
            Timber.e("Token refresh failed [${response.code()}]: $errorBody")
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

        val returnedRefreshToken = body.refreshToken
        val refreshTokenPresent = !returnedRefreshToken.isNullOrEmpty()
        val refreshTokenRotated = refreshTokenPresent && returnedRefreshToken != refreshToken
        Timber.tag(REFRESH_DIAGNOSTIC_TAG)
            .d("loginWithRefreshToken present=$refreshTokenPresent rotated=$refreshTokenRotated")

        val auth = Auth.Authenticated(
            user = user,
            accessToken = accessToken,
            refreshToken = body.refreshToken?.takeIf { it.isNotEmpty() } ?: refreshToken,
            expires = calculateExpiration(body.expiresIn)
        )
        sessionManager.save(auth)

        val enrichedUser = withLoyaltyProvider(user)
        saveUserOnCurrentSession(enrichedUser)

        Timber.d("Token refresh successful")
        return enrichedUser
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
                throw Exception(response.description ?: response.detail ?: "Google sign-in failed")
            }

            val user = response.user ?: throw Exception("Google sign-in failed")
            val accessToken = response.accessToken ?: throw Exception("Google sign-in failed")

            val auth = Auth.Authenticated(
                user = user,
                accessToken = accessToken,
                refreshToken = response.refreshToken ?: "",
                expires = calculateExpiration(response.expiresIn)
            )
            sessionManager.save(auth)

            val enrichedUser = withLoyaltyProvider(user)
            saveUserOnCurrentSession(enrichedUser)

            return enrichedUser
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: "Google sign-in failed")
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
                throw Exception(response.detail)
            }

            return response
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    // region Password Management
    override suspend fun forgotPassword(email: String): CustomerForgetPasswordResponse {
        try {
            val response = apiService.forgetPassword(CustomerForgetPassword(email = email))

            if (!response.detail.isNullOrEmpty()) {
                throw Exception(response.detail)
            }

            return response
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
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
                throw Exception(response.detail)
            }
            return response
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val message = if (errorBody != null) {
                json.decodeFromString<ChangePasswordResponse>(errorBody).detail
            } else {
                e.message
            }
            throw Exception(message)
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
                throw Exception(response.detail)
            }
            return response
        } catch (e: HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            val message = if (errorBody != null) {
                json.decodeFromString<ChangePasswordResponse>(errorBody).detail
            } else {
                e.message
            }
            throw Exception(message)
        }
    }
    // endregion

    // region Email Verification
    override suspend fun verifyEmail(email: String, token: String, orderUUID: String?): VerifyEmailResponse {
        try {
            val response = apiService.verifyEmail(VerifyEmailRequest(email, token, orderUUID))
            if (!response.isVerified) {
                throw Exception(response.detail ?: "Email verification failed")
            }
            return response
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: "Email verification failed")
        }
    }
    // endregion

    // region Profile
    override suspend fun deleteProfile(): io.esimplified.sdk.model.DeleteProfileResponse {
        return apiService.deleteProfile()
    }
    // endregion

    // region User & Preferences
    override suspend fun getUser(): Customer? {
        if (sessionManager.getAuthState() !is Auth.Authenticated) {
            return null
        }
        val user = withLoyaltyProvider(apiService.getUser())
        saveUserOnCurrentSession(user)
        return user
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
            Timber.e(e, "Failed to fetch customer preferences for loyalty provider")
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
                throw Exception(response.detail)
            }

            if (response.success == false || response.updated == false) {
                throw Exception(response.detail ?: response.message ?: "Update failed")
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
            throw Exception(parseHttpError(e) ?: "Update failed")
        }
    }
    // endregion

    // region Session
    override suspend fun logout() {
        sessionManager.save(Auth.Unauthenticated)
    }
    // endregion

    // region Private Helpers
    private fun calculateExpiration(expiresIn: Int): LocalDateTime {
        return LocalDateTime.now().plusSeconds(expiresIn.toLong())
    }

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                errorResponse.detail ?: errorResponse.message ?: errorResponse.error
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
    // endregion
}
