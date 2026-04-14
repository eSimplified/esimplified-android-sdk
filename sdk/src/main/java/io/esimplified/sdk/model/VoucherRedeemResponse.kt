package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class VoucherRedeemResponse(
    @SerialName("redeemed")
    val redeemed: Boolean,
    @SerialName("redirect_url")
    val redirectUrl: String? = null
) {
    val orderUUID: String?
        get() {
            return redirectUrl?.let { url ->
                try {
                    val uri = URI(url)
                    val query = uri.query ?: return@let null
                    query.split("&")
                        .map { it.split("=", limit = 2) }
                        .firstOrNull { it.size == 2 && it[0] == "id" }
                        ?.get(1)
                } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
                    null
                }
            }
        }
}
