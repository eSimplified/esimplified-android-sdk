package io.esimplified.sdk.model


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDetails(
    @SerialName("customer_id")
    val id: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("password")
    val password: String? = null,
    @SerialName("first_name")
    val firstName: String? = null,
    @SerialName("last_name")
    val lastName: String? = null,
    @SerialName("full_name")
    val fullName: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("referred_by")
    val referredBy: String? = null,
    @SerialName("new_customer_id")
    val newId: String? = null,
    @SerialName("new_email")
    val newEmail: String? = null,
    @SerialName("marketing_opt_in")
    val marketingConsent: Boolean? = null
)

@Serializable
data class ProfileResponse(
    @SerialName("customer_id")
    val id: String? = null,
    @SerialName("email")
    val email: String? = null,
    @SerialName("detail")
    val detail: String? = null,
    @SerialName("message")
    val message: String? = null,
    @SerialName("success")
    val success: Boolean? = null,
    @SerialName("updated")
    val updated: Boolean? = null,
    @SerialName("referral")
    val referral: String? = null,
    @SerialName("referral_code")
    val referralCode: String? = null,
)

@Serializable
data class UpdateCustomerPreferencesRequest(
    @SerialName("preferred_language")
    val preferredLanguage: String? = null,
    @SerialName("preferred_currency")
    val preferredCurrency: String? = null,
)
