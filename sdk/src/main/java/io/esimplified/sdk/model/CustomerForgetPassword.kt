package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class CustomerForgetPassword(
    @SerialName("customer_id")
    val id: String? = null,
    val email: String? = null,
)

@Serializable
data class CustomerForgetPasswordResponse(
    @SerialName("customer_id")
    val id: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("detail")
    val detail: String? = null
)
