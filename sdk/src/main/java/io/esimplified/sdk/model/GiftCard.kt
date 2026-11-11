package io.esimplified.sdk.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// region Preview

@Serializable
data class GiftCardPreviewRequest(
    @SerialName("pin")
    val pin: String
)

@Serializable
data class GiftCardPackage(
    @SerialName("package_type_id")
    val packageTypeId: Int,
    @SerialName("name")
    val name: String = "",
    @SerialName("country")
    val country: String? = null,
    @SerialName("data")
    val data: String? = null,
    @SerialName("validity_days")
    val validityDays: Int? = null
)

@Serializable
data class GiftCardPreviewResponse(
    @SerialName("package")
    val giftCardPackage: GiftCardPackage
)

// endregion

// region Redeem

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class GiftCardRedeemRequest(
    @SerialName("pin")
    val pin: String,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("marketing_opt_in")
    val marketingOptIn: Boolean? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("acquisition_source")
    val acquisitionSource: String? = DEFAULT_ACQUISITION_SOURCE
) {
    companion object {
        const val DEFAULT_ACQUISITION_SOURCE: String = "gift-card"
    }
}

@Serializable
data class GiftCardRedeemResponse(
    @SerialName("redeemed")
    val redeemed: Boolean? = null,
    @SerialName("success")
    val success: Boolean? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("order_uuid")
    val orderUUID: String? = null,
    @SerialName("package")
    val giftCardPackage: GiftCardPackage? = null,
    @SerialName("redirect_url")
    val redirectUrl: String? = null
) {
    val isRedeemed: Boolean
        get() = redeemed == true

    val awaitsEmailVerification: Boolean
        get() = !isRedeemed && success == true
}

// endregion
