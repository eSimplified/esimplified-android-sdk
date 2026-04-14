package io.esimplified.sdk.auth

interface TokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(access: String, refresh: String)
    suspend fun refreshAccessToken(): Boolean
}
