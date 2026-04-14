package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GetTokenResponse(
    @SerialName("access_token")
    val accessToken: String? = null,
    @SerialName("expires_in")
    val expiresIn: Int = 3600,
    @SerialName("token_type")
    val tokenType: String? = null,
    @SerialName("scope")
    val scope: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    @SerialName("user")
    val user: Customer? = null,
    @SerialName("error")
    val error: String? = null,
    @SerialName("detail")
    val detail: String? = null,
    @SerialName("error_description")
    val description: String? = null,
)

@Serializable
data class GetTokenIntrospectResponse(
    @SerialName("exp")
    val exp: Int? = null,
    @SerialName("scope")
    val scope: String? = null,
    @SerialName("active")
    val isActive: Boolean = false,
    @SerialName("username")
    val username: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
)
