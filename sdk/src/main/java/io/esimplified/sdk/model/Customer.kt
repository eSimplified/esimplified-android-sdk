package io.esimplified.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Customer(
    @SerialName("customer_id") val id: String,
    val email: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    @SerialName("external_reference") val externalReference: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val wallet: Double? = null,
    @SerialName("wallet_currency") val walletCurrency: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("signed_in_with_provider") val signedInWithProvider: Boolean? = null,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("preferred_currency") val preferredCurrency: String? = null,
    @SerialName("loyalty_provider") val loyaltyProvider: String? = null,
    @SerialName("mokafaa_cic_no") val mokafaaCicNo: String? = null,
    @SerialName("mokafaa_enabled") val mokafaaEnabled: Boolean? = null,
    @SerialName("mokafaa_enrollment") val mokafaaEnrollment: MokafaaEnrollment? = null,
    @SerialName("receive_emails") val receiveEmails: Boolean? = true,
    @SerialName("receive_push_notifications") val receivePushNotifications: Boolean? = true,
    @SerialName("receive_sms") val receiveSms: Boolean? = true,
) {

    companion object {
        fun Customer.details(): CustomerDetails {
            return CustomerDetails(
                email = email,
                firstName = firstName,
                lastName = lastName,
                phoneNumber = phoneNumber
            )
        }
    }
}
