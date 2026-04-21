package io.esimplified.sdk.network

import io.esimplified.sdk.SdkConfig
import io.esimplified.sdk.auth.Auth
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.model.GetTokenResponse
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.time.LocalDateTime

internal class SdkAuthInterceptor(
    private val sessionManager: SessionManager,
    private val config: SdkConfig,
) : Interceptor {

    private val json = Json { ignoreUnknownKeys = true }
    private val refreshLock = Any()

    init {
        Timber.d("SdkAuthInterceptor initialized — clientId: ${config.clientId.take(8)}..., authUrl: ${config.baseUrl}")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val credentials = "${config.clientId}:${config.clientSecret}".toByteArray().encodeBase64()

        val authState = sessionManager.getAuthState()

        Timber.d("Request: ${originalRequest.method} ${originalRequest.url} | authState: ${authState::class.simpleName}")

        val requestBuilder = originalRequest.newBuilder()
        requestBuilder.header("authorization", "Basic $credentials")
        requestBuilder.addHeader("x-auth-validation", config.awsWafToken)

        // Add currency and language headers from authenticated user preferences if available
        if (authState is Auth.Authenticated) {
            authState.user.preferredCurrency?.let { currency ->
                if (currency.isNotEmpty()) {
                    requestBuilder.addHeader("accept-currency", currency)
                }
            }
            authState.user.preferredLanguage?.let { language ->
                if (language.isNotEmpty()) {
                    requestBuilder.addHeader("accept-language", language)
                }
            }
        }

        val isAuthTokenEndpoint = originalRequest.url.toString().contains("auth/token")

        when (authState) {
            is Auth.Unauthenticated -> {
                Timber.d("Unauthenticated request -> using Basic auth")
                val notAuthenticatedRequest = requestBuilder.build()
                val response = chain.proceed(notAuthenticatedRequest)
                Timber.d("Response: ${response.code} for ${originalRequest.url}")
                return response
            }

            is Auth.Authenticated -> {
                if (authState.accessToken.isNotEmpty() && !isAuthTokenEndpoint) {
                    requestBuilder.header("authorization", "Bearer ${authState.accessToken}")
                    Timber.d("Authenticated request -> using Bearer token")
                } else if (isAuthTokenEndpoint) {
                    Timber.d("Auth token endpoint -> keeping Basic auth")
                }
                val authenticatedRequest = requestBuilder.build()
                val originalResponse = chain.proceed(authenticatedRequest)
                Timber.d("Response: ${originalResponse.code} for ${originalRequest.url}")

                if (originalResponse.isSuccessful) {
                    return originalResponse
                }

                if (originalResponse.code == 401 || originalResponse.code == 403) {
                    Timber.w("Got ${originalResponse.code} -> attempting token refresh")
                    originalResponse.close()

                    val originalAccessToken = authState.accessToken

                    synchronized(refreshLock) {
                        // Check if another thread already refreshed the token
                        val currentAuthState = sessionManager.getAuthState()
                        if (currentAuthState is Auth.Authenticated && currentAuthState.accessToken != originalAccessToken && currentAuthState.accessToken.isNotEmpty()) {
                            Timber.d("Token already refreshed by another thread")
                            val newRequestBuilder = originalRequest.newBuilder()
                            newRequestBuilder.header("authorization", "Bearer ${currentAuthState.accessToken}")
                            newRequestBuilder.addHeader("x-auth-validation", config.awsWafToken)

                            currentAuthState.user.preferredCurrency?.let { currency ->
                                if (currency.isNotEmpty()) {
                                    newRequestBuilder.addHeader("accept-currency", currency)
                                }
                            }
                            currentAuthState.user.preferredLanguage?.let { language ->
                                if (language.isNotEmpty()) {
                                    newRequestBuilder.addHeader("accept-language", language)
                                }
                            }

                            return chain.proceed(newRequestBuilder.build())
                        }

                        val refreshResponse = try {
                            val refreshRequest = createRefreshRequest(authState)
                            chain.proceed(refreshRequest)
                        } catch (e: IOException) {
                            // Network error during refresh (e.g. connectivity change)
                            // Let the caller handle the network failure
                            throw e
                        }

                        Timber.d("Refresh response: ${refreshResponse.code}")
                        return if (refreshResponse.isSuccessful) {
                            val tokens = parseTokenResponse(refreshResponse)
                            refreshResponse.close()
                            sessionManager.save(
                                authState.copy(
                                    expires = calculateExpiration(tokens.expiresIn),
                                    accessToken = tokens.accessToken ?: "",
                                    refreshToken = tokens.refreshToken?.takeIf { it.isNotEmpty() } ?: authState.refreshToken
                                )
                            )

                            // Retry original request with new token
                            val newRequestBuilder = originalRequest.newBuilder()
                            newRequestBuilder.header("authorization", "Bearer ${tokens.accessToken}")
                            newRequestBuilder.addHeader("x-auth-validation", config.awsWafToken)

                            // Re-add currency and language headers for retry
                            authState.user.preferredCurrency?.let { currency ->
                                if (currency.isNotEmpty()) {
                                    newRequestBuilder.addHeader("accept-currency", currency)
                                }
                            }
                            authState.user.preferredLanguage?.let { language ->
                                if (language.isNotEmpty()) {
                                    newRequestBuilder.addHeader("accept-language", language)
                                }
                            }

                            chain.proceed(newRequestBuilder.build())
                        } else {
                            val code = refreshResponse.code
                            Timber.e("Refresh failed with $code")
                            refreshResponse.close()
                            sessionManager.save(Auth.Unauthenticated)
                            throw IOException("Session refresh failed: $code")
                        }
                    }
                }

                return originalResponse
            }
        }
    }

    private fun createRefreshRequest(authState: Auth.Authenticated): Request {
        val credentials = "${config.clientId}:${config.clientSecret}".toByteArray().encodeBase64()
        val formBody = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", config.clientId)
            .add("client_secret", config.clientSecret)
            .add("refresh_token", authState.refreshToken)
            .build()

        return Request.Builder()
            .url("${config.baseUrl}/auth/token/")
            .header("authorization", "Basic $credentials")
            .addHeader("x-auth-validation", config.awsWafToken)
            .post(formBody)
            .build()
    }

    private fun parseTokenResponse(response: Response): GetTokenResponse {
        val responseBody = response.body?.string()
            ?: throw IOException("Response body is null")
        return try {
            json.decodeFromString<GetTokenResponse>(responseBody)
        } catch (e: Exception) {
            throw IOException("Failed to parse response body: $responseBody", e)
        }
    }

    private fun calculateExpiration(expiresIn: Int): LocalDateTime {
        return LocalDateTime.now().plusSeconds(expiresIn.toLong())
    }
}

// Extension function for base64 encoding
internal fun ByteArray.encodeBase64(): String =
    java.util.Base64.getEncoder().encodeToString(this)
