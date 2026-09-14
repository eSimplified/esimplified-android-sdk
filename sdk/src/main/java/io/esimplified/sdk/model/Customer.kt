package io.esimplified.sdk.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
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
    @SerialName("referral_code") @JsonNames("unique_referral_code") val referralCode: String? = null,
    @SerialName("acquisition_source") val acquisitionSource: String? = null,
    @SerialName("signed_in_with_provider") val signedInWithProvider: Boolean? = null,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("preferred_currency") val preferredCurrency: String? = null,
    @SerialName("loyalty_provider") val loyaltyProvider: String? = null,
    @SerialName("mokafaa_cic_no") val mokafaaCicNo: String? = null,
    @SerialName("mokafaa_enabled") val mokafaaEnabled: Boolean? = null,
    @SerialName("mokafaa_enrollment") val mokafaaEnrollment: MokafaaEnrollment? = null,
    @SerialName("receive_marketing_email") val receiveMarketingEmail: Boolean? = null,
    @SerialName("receive_marketing_push") val receiveMarketingPush: Boolean? = null,
    @SerialName("receive_account_email") val receiveAccountEmail: Boolean? = null,
    @SerialName("receive_account_sms") val receiveAccountSms: Boolean? = null,
    @SerialName("receive_account_push") val receiveAccountPush: Boolean? = null,
    @SerialName("receive_purchase_email") val receivePurchaseEmail: Boolean? = null,
    @SerialName("receive_purchase_push") val receivePurchasePush: Boolean? = null,
    @SerialName("receive_viber_messages") val receiveViberMessages: Boolean? = null,
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
