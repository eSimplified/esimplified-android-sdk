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
        val isAuthTokenEndpoint = originalRequest.url.toString().contains("auth/token")

        var authState = sessionManager.getAuthState()

        Timber.d("Request: ${originalRequest.method} ${originalRequest.url} | authState: ${authState::class.simpleName}")

        // Proactive token refresh — if token expires within 5 minutes, refresh before sending
        if (authState is Auth.Authenticated && authState.isExpired && !isAuthTokenEndpoint) {
            Timber.d("Token near expiry — proactive refresh")
            synchronized(refreshLock) {
                authState = sessionManager.getAuthState()
                if (authState is Auth.Authenticated && (authState as Auth.Authenticated).isExpired) {
                    val refreshed = attemptTokenRefresh(chain, authState as Auth.Authenticated)
                    authState = if (refreshed) {
                        sessionManager.getAuthState()
                    } else {
                        sessionManager.onAuthenticationFailed()
                        throw SdkError.AuthenticationRequired()
                    }
                }
            }
        }

        val requestBuilder = originalRequest.newBuilder()
        val credentials = "${config.clientId}:${config.clientSecret}".toByteArray().encodeBase64()

        when {
            isAuthTokenEndpoint -> {
                requestBuilder.header("authorization", "Basic $credentials")
                Timber.d("Auth token endpoint -> keeping Basic auth")
            }
            authState is Auth.Authenticated && (authState as Auth.Authenticated).accessToken.isNotEmpty() -> {
                requestBuilder.header("authorization", "Bearer ${(authState as Auth.Authenticated).accessToken}")
                Timber.d("Authenticated request -> using Bearer token")
            }
            else -> {
                requestBuilder.header("authorization", "Basic $credentials")
                Timber.d("Unauthenticated request -> using Basic auth")
            }
        }

        requestBuilder.applySecurityHeaders()

        val customHeaders = config.customHeadersProvider?.let { provider ->
            try { provider() } catch (_: Exception) { null }
        }
        if (authState is Auth.Authenticated) {
            val auth = authState as Auth.Authenticated
            if (customHeaders?.containsKey("accept-currency") != true) {
                auth.user.preferredCurrency?.takeIf { it.isNotEmpty() }?.let {
                    requestBuilder.addHeader("accept-currency", it)
                }
            }
            if (customHeaders?.containsKey("accept-language") != true) {
                auth.user.preferredLanguage?.takeIf { it.isNotEmpty() }?.let {
                    requestBuilder.addHeader("accept-language", it)
                }
            }
        }

        val response = chain.proceed(requestBuilder.build())
        Timber.d("Response: ${response.code} for ${originalRequest.url}")

        if ((response.code == 401 || response.code == 403) && authState is Auth.Authenticated && !isAuthTokenEndpoint) {
            Timber.w("Got ${response.code} -> attempting reactive token refresh")
            response.close()

            val originalAccessToken = (authState as Auth.Authenticated).accessToken

            synchronized(refreshLock) {
                val currentAuthState = sessionManager.getAuthState()
                if (currentAuthState is Auth.Authenticated && currentAuthState.accessToken != originalAccessToken && currentAuthState.accessToken.isNotEmpty()) {
                    Timber.d("Token already refreshed by another thread")
                    return chain.proceed(rebuildRequest(originalRequest, currentAuthState))
                }

                val refreshed = attemptTokenRefresh(chain, authState as Auth.Authenticated)
                if (refreshed) {
                    val newAuthState = sessionManager.getAuthState()
                    if (newAuthState is Auth.Authenticated) {
                        return chain.proceed(rebuildRequest(originalRequest, newAuthState))
                    }
                }

                sessionManager.onAuthenticationFailed()
                throw SdkError.AuthenticationRequired()
            }
        }

        return response
    }

    private fun attemptTokenRefresh(chain: Interceptor.Chain, authState: Auth.Authenticated): Boolean {
        return try {
            val refreshRequest = createRefreshRequest(authState)
            val refreshResponse = chain.proceed(refreshRequest)
            Timber.d("Refresh response: ${refreshResponse.code}")

            if (refreshResponse.isSuccessful) {
                val tokens = parseTokenResponse(refreshResponse)
                refreshResponse.close()
                sessionManager.save(
                    authState.copy(
                        expires = calculateExpiration(tokens.expiresIn),
                        accessToken = tokens.accessToken ?: "",
                        refreshToken = tokens.refreshToken?.takeIf { it.isNotEmpty() } ?: authState.refreshToken
                    )
                )
                true
            } else {
                Timber.e("Refresh failed with ${refreshResponse.code}")
                refreshResponse.close()
                false
            }
        } catch (e: IOException) {
            Timber.e("Refresh network error: ${e.message}")
            false
        }
    }

    private fun rebuildRequest(originalRequest: Request, authState: Auth.Authenticated): Request {
        val newBuilder = originalRequest.newBuilder()
        newBuilder.header("authorization", "Bearer ${authState.accessToken}")
        newBuilder.applySecurityHeaders()
        authState.user.preferredCurrency?.takeIf { it.isNotEmpty() }?.let {
            newBuilder.addHeader("accept-currency", it)
        }
        authState.user.preferredLanguage?.takeIf { it.isNotEmpty() }?.let {
            newBuilder.addHeader("accept-language", it)
        }
        return newBuilder.build()
    }

    private fun Request.Builder.applySecurityHeaders() {
        val customHeaders = config.customHeadersProvider?.let { provider ->
            try { provider() } catch (_: Exception) { null }
        }

        val wafToken = customHeaders?.get("x-auth-validation")
            ?: config.awsWafToken
        if (wafToken.isNotEmpty()) {
            addHeader("x-auth-validation", wafToken)
        }

        customHeaders?.forEach { (key, value) ->
            if (key != "x-auth-validation") {
                addHeader(key, value)
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

        val builder = Request.Builder()
            .url("${config.baseUrl}/auth/token/")
            .header("authorization", "Basic $credentials")
            .post(formBody)

        builder.applySecurityHeaders()

        return builder.build()
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

internal fun ByteArray.encodeBase64(): String =
    java.util.Base64.getEncoder().encodeToString(this)
