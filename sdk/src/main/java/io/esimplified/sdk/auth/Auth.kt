package io.esimplified.sdk.auth

import io.esimplified.sdk.model.Customer
import java.time.LocalDateTime

sealed interface Auth {
    data object Unauthenticated : Auth
    data class Authenticated(
        var user: Customer,
        val expires: LocalDateTime,
        val accessToken: String,
        val refreshToken: String
    ) : Auth
}
