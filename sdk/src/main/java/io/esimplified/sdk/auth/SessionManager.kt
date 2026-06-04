package io.esimplified.sdk.auth

interface SessionManager {
    fun isAuthenticated(): Boolean
    fun getAccessToken(): String
    fun getRefreshToken(): String
    fun getAuthState(): Auth
    fun save(auth: Auth)
    fun onAuthenticationFailed() {
        save(Auth.Unauthenticated)
    }
}
