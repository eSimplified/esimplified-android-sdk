package io.esimplified.sdk.model

import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.BaseResponse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "silent empty response" bug class.
 *
 * kotlinx.serialization's `coerceInputValues` only substitutes where a property has a default, so a
 * non-null property with no default fails the ENTIRE response when the API sends `null` for it -- not
 * just that one field. Combined with a repository that falls back to an empty collection, the user
 * sees an empty screen behind an HTTP 200.
 *
 * Every non-null property without a default is therefore a latent whole-response failure. This test
 * walks the serial descriptor graph of every model reachable from the API surface and fails when it
 * finds one that has not been deliberately accepted below.
 *
 * If this test fails, do not add the field to the allowlist to make it pass. Decide first whether the
 * API can send `null` or omit it, and prefer giving the property a neutral default (which keeps the
 * public type intact) or making it nullable.
 */
class SerializableModelGuardTest {

    // region Accepted required properties
    /** Serialized outbound only -- the SDK builds these, the API never sends them back. */
    private val outboundRequests = setOf(
        "CustomerChangePassword.new_password",
        "CustomerSignIn.email",
        "CustomerSignIn.password",
        "EsimPackageListRequest.customer_id",
        "EsimPackageListRequest.iccid",
        "EsimRequest.customer_id",
        "EsimRequest.show_balance_remaining",
        "EsimRequest.show_package_details",
        "KredsQuoteRequest.package_type_id",
        "MokafaaOtpInitiateRequest.platform",
        "MokafaaOtpInitiateRequest.purpose",
        "MokafaaOtpValidateRequest.otp",
        "MokafaaOtpValidateRequest.session_id",
        "OrderRequest.include_base64_qr_code",
        "OrderRequest.order_uuid",
        "PaymentRequest.auto_top_up",
        "PaymentRequest.customer",
        "PaymentRequest.package_type_id",
        "PaymentRequest.payment_method",
        "PaymentRequest.save_payment_method",
        "PaymentRequest.type",
        "RewardActivationRequest.reward_type",
        "VerifyEmailRequest.email",
        "VerifyEmailRequest.email_verification_token",
        "VoucherRedeemRequest.voucher_code",
    )

    /**
     * No decode path today. CountryCode comes from a bundled asset behind its own runCatching.
     * OrderInfo and QrCode hang off getOrderInfoBy, which no repository calls. RestrictedCountry is
     * decoded by the app from remote config, never by the SDK.
     */
    private val notDecodedBySdk = setOf(
        "CountryCode.code",
        "CountryCode.dial_code",
        "CountryCode.flag",
        "CountryCode.id",
        "CountryCode.limit",
        "CountryCode.name",
        "CountryCode.pattern",
        "OrderInfo.customer",
        "OrderInfo.discount_amount",
        "OrderInfo.discount_code",
        "OrderInfo.esim",
        "OrderInfo.final_price",
        "OrderInfo.iccid",
        "OrderInfo.order_type",
        "OrderInfo.order_uuid",
        "OrderInfo.package_id",
        "OrderInfo.package_name",
        "OrderInfo.package_type_id",
        "OrderInfo.purchase_country",
        "OrderInfo.purchase_currency",
        "OrderInfo.purchase_date",
        "OrderInfo.purchase_price",
        "OrderInfo.qr_code",
        "QrCode.image_base64",
        "QrCode.image_url",
        "RestrictedCountry.country_code",
        "RestrictedCountry.restriction_type",
        "RestrictedFor.country_name",
        "RestrictedFor.country_code",
    )

    /**
     * Money and epoch values. A silent 0.0 or "" here shows the customer a wrong price or date, which
     * is worse than a loud failure, and the type of each is pending an owner decision.
     */
    private val moneyAndEpoch = setOf(
        "KredsQuoteOrderCurrency.total",
        "KredsQuotePreferredPricing.total",
        "KredsQuoteUsdPricing.total",
        "KredsQuoteValue.amount",
        "OrderDetail.discount_amount",
        "OrderDetail.final_price",
        "OrderDetail.package_data_size",
        "OrderDetail.purchase_price",
        "OrderHistoryItem.discount_amount",
        "OrderHistoryItem.final_price",
        "OrderHistoryItem.purchase_price",
        "PackageDetail.date_created_epoch",
        "PackagePlan.data_GB",
        "PackagePlan.price",
    )

    /** Identity and routing keys. A blank one would silently mis-address a request or a cache entry. */
    private val identifiers = setOf(
        "Customer.customer_id",
        "OrderDetail.customer_id",
        "OrderDetail.order_number",
        "OrderDetail.package_type_id",
        "OrderDetail.package_validity",
        "OrderHistoryItem.order_number",
        "OrderHistoryItem.package_type_id",
        "PackagePlan.package_type_id",
        "PackagePlan.validity_days",
    )

    /** The payload itself, or a token the flow cannot continue without. */
    private val structural = setOf(
        "BaseResponse.results",
        "KredsQuotePricing.order_currency",
        "KredsQuoteResponse.pricing",
        "MokafaaOtpInitiateResponse.expires_at",
        "MokafaaOtpInitiateResponse.session_id",
    )

    private val accepted: Set<String> =
        outboundRequests + notDecodedBySdk + moneyAndEpoch + identifiers + structural
    // endregion

    // region Roots
    private val roots: List<KSerializer<*>> = listOf(
        ApiErrorResponse.serializer(),
        AssignedEsim.serializer(),
        AuthResponse.serializer(),
        ChangePasswordResponse.serializer(),
        CheckStockResponse.serializer(),
        CheckoutCouponRequest.serializer(),
        CheckoutCouponResponse.serializer(),
        Country.serializer(),
        CountryCode.serializer(),
        CurrencyObject.serializer(),
        Customer.serializer(),
        CustomerChangePassword.serializer(),
        CustomerDetails.serializer(),
        CustomerForgetPassword.serializer(),
        CustomerForgetPasswordResponse.serializer(),
        CustomerSignIn.serializer(),
        DeleteProfileResponse.serializer(),
        Destination.serializer(),
        DestinationFaqResponse.serializer(),
        EsimInfo.serializer(),
        EsimPackageListRequest.serializer(),
        EsimProfile.serializer(),
        EsimRequest.serializer(),
        GetTokenIntrospectResponse.serializer(),
        GetTokenResponse.serializer(),
        IframeRequest.serializer(),
        KredsLoyaltyBalanceResponse.serializer(),
        KredsQuoteRequest.serializer(),
        KredsQuoteResponse.serializer(),
        LoyaltyPointsDetail.serializer(),
        LoyaltyPointsOriginal.serializer(),
        MokafaaElection.serializer(),
        MokafaaEnrollment.serializer(),
        MokafaaOtpInitiateRequest.serializer(),
        MokafaaOtpInitiateResponse.serializer(),
        MokafaaOtpValidateRequest.serializer(),
        MokafaaOtpValidateResponse.serializer(),
        NotificationSettings.serializer(),
        OrderDetail.serializer(),
        OrderHistoryItem.serializer(),
        OrderInfo.serializer(),
        OrderRequest.serializer(),
        OrdersPage.serializer(),
        PackageDetail.serializer(),
        PackagePlan.serializer(),
        PaymentRequest.serializer(),
        PaymentResponse.serializer(),
        ProfileResponse.serializer(),
        ProfileReusePolicy.serializer(),
        PurchaseCountry.serializer(),
        QrCode.serializer(),
        RatingApiResponse.serializer(),
        RestrictedCountry.serializer(),
        RestrictedFor.serializer(),
        RewardActivationRequest.serializer(),
        SupportedCountry.serializer(),
        ThemeResponse.serializer(),
        Transaction.serializer(),
        UpdateCustomerPreferencesRequest.serializer(),
        UserLocationResponse.serializer(),
        VerifyEmailRequest.serializer(),
        VerifyEmailResponse.serializer(),
        VisaRewardsIframeResponse.serializer(),
        VisaRewardsResponse.serializer(),
        VoucherRedeemRequest.serializer(),
        VoucherRedeemResponse.serializer(),
        BaseResponse.serializer(ListSerializer(Country.serializer())),
    )
    // endregion

    @Test
    fun `no serializable model gains a non-null property without a default`() {
        val findings = walk().findings

        val unexpected = (findings - accepted).sorted()
        assertTrue(
            "These properties are non-null with no default, so a null from the API fails the whole " +
                "response instead of just the field. Give each a neutral default or make it nullable: " +
                unexpected.joinToString(", "),
            unexpected.isEmpty(),
        )
    }

    @Test
    fun `the accepted list carries no entry that has already been fixed`() {
        val findings = walk().findings

        val stale = (accepted - findings).sorted()
        assertTrue(
            "These accepted entries no longer exist, so the allowlist is out of date: " +
                stale.joinToString(", "),
            stale.isEmpty(),
        )
    }

    @Test
    fun `every model on the api surface is covered by a root serializer`() {
        val walked = walk().types
        val onApiSurface = apiSurfaceTypeNames()

        assertTrue("Expected the api surface to name several models", onApiSurface.size > 20)
        val missing = (onApiSurface - walked).sorted()
        assertEquals(
            "These types appear in ApiService but no root serializer reaches them, so the guard " +
                "above cannot see their properties. Add each to roots: " + missing.joinToString(", "),
            emptyList<String>(),
            missing,
        )
    }

    // region Descriptor walk
    private data class Walk(val findings: Set<String>, val types: Set<String>)

    private fun walk(): Walk {
        val types = mutableSetOf<String>()
        val findings = mutableSetOf<String>()

        fun visit(descriptor: SerialDescriptor) {
            if (descriptor.kind != StructureKind.CLASS && descriptor.kind != StructureKind.OBJECT) {
                for (i in 0 until descriptor.elementsCount) visit(descriptor.getElementDescriptor(i))
                return
            }
            val name = descriptor.serialName.trimEnd('?')
            if (!types.add(name)) return
            for (i in 0 until descriptor.elementsCount) {
                val element = descriptor.getElementDescriptor(i)
                if (!descriptor.isElementOptional(i) && !element.isNullable) {
                    findings += "${name.substringAfterLast('.')}.${descriptor.getElementName(i)}"
                }
                visit(element)
            }
        }

        roots.forEach { visit(it.descriptor) }
        return Walk(findings, types)
    }

    private fun apiSurfaceTypeNames(): Set<String> = ApiService::class.java.methods
        .flatMap { method ->
            method.genericParameterTypes.map { it.typeName } + method.genericReturnType.typeName
        }
        .flatMap { SDK_TYPE_PATTERN.findAll(it).map { match -> match.value } }
        .filterNot { it == ApiService::class.java.name }
        .toSet()
    // endregion

    private companion object {
        val SDK_TYPE_PATTERN =
            Regex("""io\.esimplified\.sdk\.(?:model|network)\.[A-Za-z0-9_]+""")
    }
}
