package io.esimplified.sdk.repository

import io.esimplified.sdk.model.ChangePasswordResponse
import io.esimplified.sdk.model.CustomerForgetPasswordResponse
import io.esimplified.sdk.model.ProfileResponse
import io.esimplified.sdk.model.VerifyEmailResponse
import io.esimplified.sdk.model.Customer
import io.esimplified.sdk.model.DeleteProfileResponse

interface AuthRepository {

    // region Authentication
    suspend fun login(email: String, password: String): Customer
    suspend fun loginWithRefreshToken(refreshToken: String): Customer
    suspend fun signInWithGoogle(
        email: String,
        firstName: String,
        lastName: String,
        fullName: String,
        phoneNumber: String,
        providerAccountId: String,
        idToken: String
    ): Customer
    // endregion

    // region Registration
    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phoneNumber: String,
        marketingConsent: Boolean?,
        referredBy: String? = null
    ): ProfileResponse
    // endregion

    // region Password Management
    suspend fun forgotPassword(email: String): CustomerForgetPasswordResponse
    suspend fun changePassword(currentPassword: String, newPassword: String): ChangePasswordResponse
    suspend fun resetPassword(email: String, token: String, newPassword: String): ChangePasswordResponse
    // endregion

    // region Email Verification
    suspend fun verifyEmail(email: String, token: String, orderUUID: String?): VerifyEmailResponse
    // endregion

    // region Profile
    suspend fun deleteProfile(): DeleteProfileResponse
    // endregion

    // region User & Preferences
    suspend fun getUser(): Customer?
    suspend fun updatePreferences(preferredLanguage: String?, preferredCurrency: String?): Customer
    suspend fun updateProfile(
        email: String,
        firstName: String?,
        lastName: String?,
        phoneNumber: String?,
        password: String
    ): ProfileResponse
    // endregion

    // region Session
    suspend fun logout()
    // endregion
}
