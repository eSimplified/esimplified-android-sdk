package io.esimplified.sdk.network

class LoyaltyApiException(
    val httpCode: Int,
    override val message: String?,
) : Exception(message)
