package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class CustomerChangePassword(
    @SerialName("customer_id")
    val id: String? = null,
    val email: String? = null,
    @SerialName("password_reset_token")
    val token: String? = null,
    val password: String? = null,
    @SerialName("new_password")
    val newPassword: String,
)

@Serializable
data class ChangePasswordResponse(
    @SerialName("customer_details")
    val customer: Customer? = null,
    @SerialName("password_reset")
    val success: Boolean = false,
    val detail: String? = null,
)
