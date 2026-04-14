package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileReusePolicy(
    @SerialName("reuse_type") var type: String? = null,
    @SerialName("max_count") var count: Int? = null
)

@Serializable
data class EsimProfile(
    @SerialName("eid") var eid: String? = null,
    @SerialName("imsi") var imsi: Long? = null,
    @SerialName("iccid") var iccid: String? = null,
    @SerialName("state") var state: EsimProfileState? = null,
    @SerialName("cc_required") var ccRequired: Boolean? = null,
    @SerialName("state_message") var status: String? = null,
    @SerialName("release_date") var releaseDate: Long? = null,
    @SerialName("reuse_enabled") var reuseEnabled: Boolean? = null,
    @SerialName("activation_code") var activationCode: String? = null,
    @SerialName("release_date_utc") var releaseDateUtc: String? = null,
    @SerialName("last_operation_date") var lastOperationDate: Long? = null,
    @SerialName("profile_reuse_policy") var policy: ProfileReusePolicy? = null,
    @SerialName("reuse_remaining_count") var reuseRemainingCount: Int? = null,
    @SerialName("last_operation_date_utc") var lastOperationDateUtc: String? = null,
) {
    val installed: Boolean = when (state) {
        EsimProfileState.ENABLED, EsimProfileState.DOWNLOADED, EsimProfileState.INSTALLED, EsimProfileState.DISABLED -> true
        else -> false
    }

    val isDeleted: Boolean = state == EsimProfileState.DELETED
}

@Serializable
enum class EsimProfileState {
    @SerialName("ENABLED")
    ENABLED,

    @SerialName("DOWNLOADED")
    DOWNLOADED,

    @SerialName("INSTALLED")
    INSTALLED,

    @SerialName("DISABLED")
    DISABLED,

    @SerialName("DELETED")
    DELETED,

    @SerialName("RELEASED")
    RELEASED,

    @SerialName("ERROR")
    ERROR,
}

@Serializable
data class OrderDetail(
    @SerialName("detail") val detail: String? = null,
    @SerialName("iccid") val iccid: String? = null,
    @SerialName("country") val country: Country,
    @SerialName("profile") val esimProfile: EsimProfile? = null,
    @SerialName("qr_code") val qrCode: String? = null,
    @SerialName("qr_code_image_base64") val qrCodeImageBase64: String? = null,
    @SerialName("activation_code") val activationCode: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    @SerialName("customer_id") val customerId: String,
    @SerialName("discount_amount") val discountAmount: Double,
    @SerialName("discount_code") val discountCode: String,
    @SerialName("final_price") val finalPrice: Double,
    @SerialName("order_date") val orderDate: String,
    @SerialName("order_number") val orderNumber: Int,
    @SerialName("order_status") val orderStatus: String,
    @SerialName("order_type") val orderType: String,
    @SerialName("package_data_size") val packageDataSize: Double,
    @SerialName("package_type_id") val packageTypeId: Int,
    @SerialName("package_name") val packageName: String,
    @SerialName("package_validity") val packageValidity: Int,
    @SerialName("password_reset_encoded") val passwordResetEncoded: String? = null,
    @SerialName("purchase_currency") val currency: String,
    @SerialName("purchase_currency_obj") val currencyObject: CurrencyObject,
    @SerialName("purchase_price") val price: Double,
    @SerialName("points_earned") val loyaltyPointsEarned: LoyaltyPointsDetail? = null,
    @SerialName("points_spent") val loyaltyPointsSpent: LoyaltyPointsDetail? = null,
    @SerialName("sm_dp_address") val smDpAddress: String? = null,
    @SerialName("payment_method") val paymentMethod: PaymentMethod,
    @SerialName("transaction_id") val transactionId: String? = null,
    @SerialName("conversion_tracked") val tracked: Boolean = false
)
