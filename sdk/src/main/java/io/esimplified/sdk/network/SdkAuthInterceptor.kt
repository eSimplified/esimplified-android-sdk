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
import io.esimplified.sdk.SdkLog
import io.esimplified.sdk.redactedPath
import java.io.IOException
import java.time.LocalDateTime

internal class SdkAuthInterceptor(
    private val sessionManager: SessionManager,
    private val config: SdkConfig,
) : Interceptor {

    private val json = Json { ignoreUnknownKeys = true }

    private sealed interface RefreshOutcome {
        data object Success : RefreshOutcome
        data object AuthRejected : RefreshOutcome
        data class Retryable(val cause: IOException) : RefreshOutcome
    }

    init {
        SdkLog.d("SdkAuthInterceptor initialized — authUrl: ${config.baseUrl}")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val isAuthTokenEndpoint = originalRequest.url.toString().contains("auth/token")

        var authState = sessionManager.getAuthState()

        SdkLog.d(
            "Request: ${originalRequest.method} ${originalRequest.url.encodedPath.redactedPath()}" +
                " | authState: ${authState::class.simpleName}"
        )

        // Proactive token refresh — if token expires within 5 minutes, refresh before sending
        if (authState is Auth.Authenticated && authState.isExpired && !isAuthTokenEndpoint) {
            SdkLog.d("Token near expiry — proactive refresh")
            TokenRefreshGate.withRefreshPermit {
                authState = sessionManager.getAuthState()
                if (authState is Auth.Authenticated && (authState as Auth.Authenticated).isExpired) {
                    authState = when (val outcome = attemptTokenRefresh(chain, authState as Auth.Authenticated)) {
                        is RefreshOutcome.Success -> sessionManager.getAuthState()
                        is RefreshOutcome.Retryable -> throw outcome.cause
                        RefreshOutcome.AuthRejected -> {
                            sessionManager.onAuthenticationFailed()
                            throw SdkError.AuthenticationRequired()
                        }
                    }
                }
            }
        }

        val requestBuilder = originalRequest.newBuilder()
        val credentials = "${config.clientId}:${config.clientSecret}".toByteArray().encodeBase64()

        when {
            isAuthTokenEndpoint -> {
                requestBuilder.header("authorization", "Basic $credentials")
                SdkLog.d("Auth token endpoint -> keeping Basic auth")
            }
            authState is Auth.Authenticated && (authState as Auth.Authenticated).accessToken.isNotEmpty() -> {
                requestBuilder.header("authorization", "Bearer ${(authState as Auth.Authenticated).accessToken}")
                SdkLog.d("Authenticated request -> using Bearer token")
            }
            else -> {
                requestBuilder.header("authorization", "Basic $credentials")
                SdkLog.d("Unauthenticated request -> using Basic auth")
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
        SdkLog.d("Response: ${response.code} for ${originalRequest.url.encodedPath.redactedPath()}")

        if (response.code == 401 && authState is Auth.Authenticated && !isAuthTokenEndpoint) {
            SdkLog.w("Got ${response.code} -> attempting reactive token refresh")
            response.close()

            val originalAccessToken = (authState as Auth.Authenticated).accessToken

            TokenRefreshGate.withRefreshPermit {
                val currentAuthState = sessionManager.getAuthState()
                if (currentAuthState is Auth.Authenticated && currentAuthState.accessToken != originalAccessToken && currentAuthState.accessToken.isNotEmpty()) {
                    SdkLog.d("Token already refreshed by another thread")
                    return chain.proceed(rebuildRequest(originalRequest, currentAuthState))
                }

                val refreshFrom = currentAuthState as? Auth.Authenticated ?: (authState as Auth.Authenticated)
                when (val outcome = attemptTokenRefresh(chain, refreshFrom)) {
                    is RefreshOutcome.Success -> {
                        val newAuthState = sessionManager.getAuthState()
                        if (newAuthState is Auth.Authenticated) {
                            return chain.proceed(rebuildRequest(originalRequest, newAuthState))
                        }
                        sessionManager.onAuthenticationFailed()
                        throw SdkError.AuthenticationRequired()
                    }
                    is RefreshOutcome.Retryable -> throw outcome.cause
                    RefreshOutcome.AuthRejected -> {
                        sessionManager.onAuthenticationFailed()
                        throw SdkError.AuthenticationRequired()
                    }
                }
            }
        }

        return response
    }

    private fun attemptTokenRefresh(chain: Interceptor.Chain, authState: Auth.Authenticated): RefreshOutcome {
        if (authState.refreshToken.isEmpty()) {
            SdkLog.e("No refresh token to refresh with — ending the session")
            return RefreshOutcome.AuthRejected
        }

        val refreshResponse = try {
            chain.proceed(createRefreshRequest(authState))
        } catch (networkError: IOException) {
            SdkLog.e("Refresh network error", networkError)
            return RefreshOutcome.Retryable(networkError)
        }

        return try {
            SdkLog.d("Refresh response: ${refreshResponse.code}")
            if (refreshResponse.isSuccessful) {
                val tokens = parseTokenResponse(refreshResponse)
                val accessToken = tokens.accessToken?.takeIf { it.isNotEmpty() }
                if (accessToken == null) {
                    SdkLog.e("Refresh succeeded without an access token — keeping the session")
                    RefreshOutcome.Retryable(
                        SdkError.NetworkError(refreshResponse.code, "The token response carried no access token")
                    )
                } else {
                    val base = (sessionManager.getAuthState() as? Auth.Authenticated)
                        ?.takeIf { it.refreshToken == authState.refreshToken }
                        ?: authState
                    sessionManager.save(
                        base.copy(
                            expires = calculateExpiration(tokens.expiresIn),
                            accessToken = accessToken,
                            refreshToken = tokens.refreshToken?.takeIf { it.isNotEmpty() } ?: authState.refreshToken
                        )
                    )
                    RefreshOutcome.Success
                }
            } else {
                val body = readBody(refreshResponse)
                if (TokenRefresh.isSessionRejection(refreshResponse.code, body)) {
                    SdkLog.e("Refresh rejected with ${refreshResponse.code} — ending the session")
                    RefreshOutcome.AuthRejected
                } else {
                    val message = ApiErrorMessage.parseOrNull(body)
                        ?: refreshResponse.message.ifEmpty { ApiErrorMessage.FALLBACK }
                    SdkLog.e("Refresh failed with ${refreshResponse.code} — keeping the session")
                    RefreshOutcome.Retryable(SdkError.NetworkError(refreshResponse.code, message))
                }
            }
        } catch (parseError: IOException) {
            SdkLog.e("Refresh response parse error", parseError)
            RefreshOutcome.Retryable(parseError)
        } finally {
            refreshResponse.close()
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
            throw IOException(
                "Failed to parse the token response" +
                    " (${responseBody.length} chars, ${e::class.simpleName})"
            )
        }
    }

    private fun calculateExpiration(expiresIn: Int): LocalDateTime {
        return LocalDateTime.now().plusSeconds(expiresIn.toLong())
    }

    private fun readBody(response: Response): String? =
        runCatching { response.body?.string() }.getOrNull()
}

internal fun ByteArray.encodeBase64(): String =
    java.util.Base64.getEncoder().encodeToString(this)
