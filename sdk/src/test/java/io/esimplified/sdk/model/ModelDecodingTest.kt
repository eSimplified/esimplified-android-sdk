package io.esimplified.sdk.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding round-trip tests for every public response model.
 * Catches API drift / serial-name regressions.
 */
class ModelDecodingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val productionJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    // MARK: - Customer / Auth

    @Test
    fun `Customer decodes all snake_case fields`() {
        val payload = """
            {
                "customer_id": "u-1",
                "email": "a@b.com",
                "phone_number": "+12345",
                "external_reference": "ext",
                "first_name": "Alice",
                "last_name": "Smith",
                "full_name": "Alice Smith",
                "wallet": 5.0,
                "wallet_currency": "USD",
                "referral_code": "REF",
                "signed_in_with_provider": true,
                "preferred_language": "en",
                "preferred_currency": "USD"
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("u-1", customer.id)
        assertEquals("a@b.com", customer.email)
        assertEquals("+12345", customer.phoneNumber)
        assertEquals("Alice", customer.firstName)
        assertEquals("Smith", customer.lastName)
        assertEquals("Alice Smith", customer.fullName)
        assertEquals(5.0, customer.wallet)
        assertEquals("USD", customer.walletCurrency)
        assertEquals("REF", customer.referralCode)
        assertEquals(true, customer.signedInWithProvider)
        assertEquals("en", customer.preferredLanguage)
        assertEquals("USD", customer.preferredCurrency)
    }

    @Test
    fun `Customer decodes with minimum required field only`() {
        val payload = """{"customer_id": "u-1"}"""
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("u-1", customer.id)
        assertNull(customer.email)
        assertNull(customer.firstName)
    }

    @Test
    fun `GetTokenResponse decodes oauth fields`() {
        val payload = """
            {
                "access_token": "at",
                "refresh_token": "rt",
                "expires_in": 3600,
                "token_type": "Bearer",
                "scope": "all"
            }
        """.trimIndent()
        val r = json.decodeFromString<GetTokenResponse>(payload)
        assertEquals("at", r.accessToken)
        assertEquals("rt", r.refreshToken)
        assertEquals(3600, r.expiresIn)
        assertEquals("Bearer", r.tokenType)
    }

    @Test
    fun `GetTokenResponse handles error payload shape`() {
        val payload = """{"error":"invalid_grant","error_description":"Bad credentials"}"""
        val r = json.decodeFromString<GetTokenResponse>(payload)
        assertEquals("invalid_grant", r.error)
        assertEquals("Bad credentials", r.description)
    }

    // MARK: - Country

    @Test
    fun `Country decodes snake_case fields`() {
        val payload = """
            {
                "country_name": "Canada",
                "country_code": "CA",
                "country_name_slug": "canada",
                "country_flag": "🇨🇦",
                "country_flag_css": "ca",
                "is_region": false
            }
        """.trimIndent()
        val country = json.decodeFromString<Country>(payload)
        assertEquals("Canada", country.name)
        assertEquals("CA", country.code)
        assertEquals("canada", country.slug)
        assertEquals("🇨🇦", country.flag)
        assertEquals(false, country.isRegion)
    }

    // MARK: - Promo / Voucher / Visa

    @Test
    fun `CheckoutCouponResponse decodes valid and discount fields`() {
        val payload = """
            {
                "valid": true,
                "detail": "applied",
                "discount_code": "SAVE10",
                "discount_percentage": 0.10,
                "product_type": "esim"
            }
        """.trimIndent()
        val r = json.decodeFromString<CheckoutCouponResponse>(payload)
        assertEquals(true, r.valid)
        assertEquals("applied", r.detail)
        assertEquals("SAVE10", r.discount)
        assertEquals(0.10, r.percentage)
        assertEquals("esim", r.productType)
    }

    @Test
    fun `VoucherRedeemResponse decodes redirect_url and extracts order id from query`() {
        val payload = """
            {
                "redeemed": true,
                "redirect_url": "https://example.com/o?id=order-abc"
            }
        """.trimIndent()
        val r = json.decodeFromString<VoucherRedeemResponse>(payload)
        assertEquals(true, r.redeemed)
        assertEquals("https://example.com/o?id=order-abc", r.redirectUrl)
        assertEquals("order-abc", r.orderUUID)
    }

    @Test
    fun `VoucherRedeemResponse orderUUID is null when redirect_url has no id query`() {
        val payload = """{"redeemed":true,"redirect_url":"https://example.com/o/abc"}"""
        val r = json.decodeFromString<VoucherRedeemResponse>(payload)
        assertNull(r.orderUUID)
    }

    @Test
    fun `VisaRewardsResponse decodes eligibility and reward data`() {
        val payload = """
            {
                "eligible": true,
                "status": 200,
                "redeemed": false,
                "allowed_count": 3,
                "remaining_count": 2,
                "used_count": 1,
                "reward_type": "DISCOUNT",
                "validity_days": 30,
                "data_GB": 5.0
            }
        """.trimIndent()
        val r = json.decodeFromString<VisaRewardsResponse>(payload)
        assertEquals(true, r.eligible)
        assertEquals(200, r.status)
        assertEquals(3, r.allowed)
        assertEquals(2, r.remaining)
        assertEquals(1, r.used)
        assertEquals("DISCOUNT", r.reward)
        assertEquals(30, r.validityDays)
        assertEquals(5.0, r.dataGB)
    }

    @Test
    fun `VisaRewardsIframeResponse decodes iframe url and token`() {
        val payload = """
            {
                "iframe_url": "https://visa.example.com/iframe",
                "token": "tok-123"
            }
        """.trimIndent()
        val r = json.decodeFromString<VisaRewardsIframeResponse>(payload)
        assertEquals("https://visa.example.com/iframe", r.iframeUrl)
        assertEquals("tok-123", r.token)
    }

    // MARK: - Notifications / misc

    @Test
    fun `NotificationSettings decodes type and enabled`() {
        val payload = """{"type":"marketing","enabled":true}"""
        val r = json.decodeFromString<NotificationSettings>(payload)
        assertEquals("marketing", r.type)
        assertEquals(true, r.enabled)
    }

    @Test
    fun `DeleteProfileResponse decodes deleted flag`() {
        val payload = """{"deleted":true}"""
        val r = json.decodeFromString<DeleteProfileResponse>(payload)
        assertEquals(true, r.deleted)
    }

    @Test
    fun `ApiErrorResponse decodes detail and error`() {
        val payload = """{"detail":"Bad input","error":"validation"}"""
        val r = json.decodeFromString<ApiErrorResponse>(payload)
        assertEquals("Bad input", r.detail)
        assertEquals("validation", r.error)
    }

    @Test
    fun `ApiErrorResponse handles null fields`() {
        val payload = """{"detail":null,"error":null}"""
        val r = json.decodeFromString<ApiErrorResponse>(payload)
        assertNull(r.detail)
        assertNull(r.error)
    }

    // MARK: - User location

    @Test
    fun `UserLocationResponse decodes nested location`() {
        val payload = """
            {
                "location": {
                    "country": "United Arab Emirates",
                    "countryCode": "AE",
                    "city": "Dubai",
                    "lat": 25.0,
                    "lon": 55.0,
                    "timezone": "Asia/Dubai"
                }
            }
        """.trimIndent()
        val r = json.decodeFromString<UserLocationResponse>(payload)
        assertNotNull(r.location)
        assertEquals("United Arab Emirates", r.location?.country)
        assertEquals("AE", r.location?.countryCode)
        assertEquals("Dubai", r.location?.city)
    }

    // MARK: - Forgot / Change password

    @Test
    fun `CustomerForgetPasswordResponse decodes detail`() {
        val payload = """
            {"customer_id":"c-1","email":"a@b.com","detail":"Sent"}
        """.trimIndent()
        val r = json.decodeFromString<CustomerForgetPasswordResponse>(payload)
        assertEquals("c-1", r.id)
        assertEquals("a@b.com", r.email)
        assertEquals("Sent", r.detail)
    }

    @Test
    fun `ChangePasswordResponse decodes success and detail`() {
        val payload = """{"detail":"Password updated","success":true}"""
        val r = json.decodeFromString<ChangePasswordResponse>(payload)
        assertEquals("Password updated", r.detail)
    }

    // MARK: - Verify email

    @Test
    fun `VerifyEmailResponse decodes verified status`() {
        val payload = """{"email_verified":true,"email":"a@b.com","detail":"ok"}"""
        val r = json.decodeFromString<VerifyEmailResponse>(payload)
        assertEquals(true, r.isVerified)
        assertEquals("a@b.com", r.email)
    }

    // MARK: - Profile

    @Test
    fun `ProfileResponse decodes customer fields`() {
        val payload = """
            {
                "customer_id": "c-1",
                "email": "a@b.com",
                "first_name": "A",
                "last_name": "B",
                "success": true
            }
        """.trimIndent()
        val r = json.decodeFromString<ProfileResponse>(payload)
        assertEquals("c-1", r.id)
        assertEquals("a@b.com", r.email)
        assertEquals(true, r.success)
    }

    @Test
    fun `ProfileResponse decodes mokafaa election`() {
        val payload = """
            {
                "customer_id": "c-1",
                "email": "a@b.com",
                "success": true,
                "mokafaa": {"elected": true}
            }
        """.trimIndent()
        val r = json.decodeFromString<ProfileResponse>(payload)
        assertEquals(true, r.mokafaa?.elected)
    }

    @Test
    fun `Customer decodes mokafaa_enrollment state and session expiry`() {
        val payload = """
            {
                "customer_id": "u-1",
                "mokafaa_enrollment": {"state": "pending", "session_expires_at": "2026-06-11T10:05:00.000Z"}
            }
        """.trimIndent()
        val r = json.decodeFromString<Customer>(payload)
        assertEquals(MokafaaEnrollment.State.PENDING, r.mokafaaEnrollment?.state)
        assertEquals("2026-06-11T10:05:00.000Z", r.mokafaaEnrollment?.sessionExpiresAt)
    }

    @Test
    fun `MokafaaEnrollment tolerates unknown state strings`() {
        val payload = """{"customer_id": "u-1", "mokafaa_enrollment": {"state": "some_future_state"}}"""
        val r = json.decodeFromString<Customer>(payload)
        assertEquals("some_future_state", r.mokafaaEnrollment?.state)
        assertNull(r.mokafaaEnrollment?.sessionExpiresAt)
    }

    @Test
    fun `CustomerDetails serializes loyalty_election`() {
        val body = Json.encodeToString(
            CustomerDetails.serializer(),
            CustomerDetails(email = "a@b.com", loyaltyElection = LoyaltyProvider.MOKAFAA)
        )
        assertTrue(body.contains(""""loyalty_election":"mokafaa""""))
    }

    // MARK: - Loyalty

    @Test
    fun `KredsLoyaltyBalanceResponse decodes total points`() {
        val payload = """
            {
                "total_loyalty_points": 1500,
                "total_loyalty_points_detail": {
                    "amount": "15.00",
                    "currency": {
                        "symbol": "$",
                        "iso": "USD"
                    }
                }
            }
        """.trimIndent()
        val r = json.decodeFromString<KredsLoyaltyBalanceResponse>(payload)
        assertEquals(1500, r.totalLoyaltyPoints)
        assertEquals("15.00", r.totalLoyaltyPointsDetail.amount)
        assertEquals("$", r.totalLoyaltyPointsDetail.currency.symbol)
        assertEquals("USD", r.totalLoyaltyPointsDetail.currency.isoCode)
    }

    // MARK: - Destination

    @Test
    fun `Destination supports partial fields`() {
        // Destination is created code-side, not API-decoded — verify serializable contract
        val dest = Destination(code = "US", name = "United States", slug = "united-states", region = null)
        val encoded = json.encodeToString(Destination.serializer(), dest)
        assertTrue(encoded.contains("US"))
    }

    // MARK: - Currency

    @Test
    fun `CurrencyObject decodes symbol and iso`() {
        val payload = """{"symbol":"$","iso":"USD"}"""
        val r = json.decodeFromString<CurrencyObject>(payload)
        assertEquals("$", r.symbol)
        assertEquals("USD", r.isoCode)
    }

    @Test
    fun `Customer ignores unknown keys`() {
        val payload = """
            {
                "customer_id": "u-1",
                "email": "a@b.com",
                "unknown_future_field": "should be ignored",
                "another_one": 42
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("u-1", customer.id)
        assertEquals("a@b.com", customer.email)
    }

    // MARK: - Assigned eSIM

    @Test
    fun `AssignedEsim list decodes when one entry has no profile`() {
        val payload = """
            [
                {
                    "iccid": "8910-installed",
                    "esim_name": "Installed eSIM",
                    "assigned_date": "2026-01-01",
                    "archived": false,
                    "auto_top_up": false,
                    "profile": {
                        "iccid": "8910-installed",
                        "state": "INSTALLED",
                        "state_message": "Installed"
                    }
                },
                {
                    "iccid": "8910-released",
                    "esim_name": "Freshly bought eSIM",
                    "assigned_date": "2026-01-02",
                    "archived": false,
                    "auto_top_up": false
                }
            ]
        """.trimIndent()
        val esims = json.decodeFromString<List<AssignedEsim>>(payload)
        assertEquals(2, esims.size)
        assertNotNull(esims[0].profile)
        assertEquals(EsimProfileState.INSTALLED, esims[0].profile?.state)
        assertNull(esims[1].profile)
        assertEquals("8910-released", esims[1].iccid)
    }

    @Test
    fun `AssignedEsim decodes explicit null profile`() {
        val payload = """
            {
                "iccid": "8910-null",
                "esim_name": null,
                "assigned_date": "2026-01-03",
                "archived": false,
                "auto_top_up": false,
                "profile": null
            }
        """.trimIndent()
        val esim = json.decodeFromString<AssignedEsim>(payload)
        assertNull(esim.profile)
        assertNull(esim.name)
    }

    @Test
    fun `EsimInfo decodes android_sha`() {
        val payload = """
            {
                "assigned_date": "2026-01-01",
                "iccid": "8910",
                "matching_id": "MATCH",
                "premium": false,
                "sm_dp_address": "sm-dp.example.com",
                "android_sha": true
            }
        """.trimIndent()
        val info = json.decodeFromString<EsimInfo>(payload)
        assertTrue(info.androidSha)
    }

    @Test
    fun `EsimInfo defaults android_sha to false when absent`() {
        val payload = """
            {
                "assigned_date": "2026-01-01",
                "iccid": "8910",
                "matching_id": "MATCH",
                "premium": false,
                "sm_dp_address": "sm-dp.example.com"
            }
        """.trimIndent()
        val info = json.decodeFromString<EsimInfo>(payload)
        assertFalse(info.androidSha)
    }

    @Test
    fun `AssignedEsim decodes is_primary and is_universal and epoch fields`() {
        val payload = """
            {
                "iccid": "8910-primary",
                "esim_name": "Primary eSIM",
                "assigned_date": "2026-01-01",
                "archived": false,
                "auto_top_up": true,
                "android_sha": true,
                "order_number": "ORD-1",
                "date_activated_epoch": 1735689600,
                "date_expiry_epoch": 1738368000,
                "days_left_to_expiry": 31,
                "is_primary": true,
                "is_universal": true
            }
        """.trimIndent()
        val esim = json.decodeFromString<AssignedEsim>(payload)
        assertTrue(esim.isPrimary)
        assertTrue(esim.isUniversal)
        assertTrue(esim.androidSha)
        assertEquals("ORD-1", esim.orderNumber)
        assertEquals(1735689600L, esim.dateActivatedEpoch)
        assertEquals(1738368000L, esim.dateExpiryEpoch)
        assertEquals(31, esim.daysLeftToExpiry)
    }

    @Test
    fun `AssignedEsim defaults new flags when payload predates them`() {
        val payload = """
            {
                "iccid": "8910-legacy",
                "esim_name": "Legacy eSIM",
                "assigned_date": "2026-01-01",
                "archived": false,
                "auto_top_up": false
            }
        """.trimIndent()
        val esim = json.decodeFromString<AssignedEsim>(payload)
        assertFalse(esim.isPrimary)
        assertFalse(esim.isUniversal)
        assertFalse(esim.androidSha)
        assertNull(esim.orderNumber)
        assertNull(esim.dateActivatedEpoch)
        assertNull(esim.dateExpiryEpoch)
        assertNull(esim.daysLeftToExpiry)
    }

    @Test
    fun `EsimInfo decodes country esim_name and is_universal`() {
        val payload = """
            {
                "assigned_date": "2026-01-01",
                "iccid": "8910",
                "matching_id": "MATCH",
                "premium": true,
                "sm_dp_address": "sm-dp.example.com",
                "country": "Australia",
                "esim_name": "My eSIM",
                "is_universal": true
            }
        """.trimIndent()
        val info = json.decodeFromString<EsimInfo>(payload)
        assertEquals("Australia", info.country)
        assertEquals("My eSIM", info.esimName)
        assertTrue(info.isUniversal)
    }

    @Test
    fun `EsimInfo defaults country and is_universal when absent`() {
        val payload = """
            {
                "assigned_date": "2026-01-01",
                "iccid": "8910",
                "matching_id": "MATCH",
                "premium": false,
                "sm_dp_address": "sm-dp.example.com"
            }
        """.trimIndent()
        val info = json.decodeFromString<EsimInfo>(payload)
        assertEquals("", info.country)
        assertNull(info.esimName)
        assertFalse(info.isUniversal)
    }

    // MARK: - Package Detail

    @Test
    fun `PackageDetail decodes data_usage_bytes package_country_code and status_message`() {
        val payload = """
            {
                "status": "ACTIVE",
                "date_created_epoch": 1735689600,
                "package_country_name": "Australia",
                "package_country_code": "AU",
                "data_usage_bytes": 1073741824.0,
                "status_message": "Package Activated"
            }
        """.trimIndent()
        val detail = json.decodeFromString<PackageDetail>(payload)
        assertEquals("AU", detail.packageCountryCode)
        assertEquals(1073741824.0, detail.dataUsedBytes)
        assertEquals("Package Activated", detail.statusMessage)
    }

    @Test
    fun `PackageDetail decodes payload without the new fields`() {
        val payload = """
            {
                "status": "NOT_ACTIVE",
                "date_created_epoch": 1735689600,
                "package_country_name": "Australia"
            }
        """.trimIndent()
        val detail = json.decodeFromString<PackageDetail>(payload)
        assertNull(detail.packageCountryCode)
        assertNull(detail.dataUsedBytes)
        assertEquals("", detail.statusMessage)
        assertEquals("Australia", detail.packageCountryName)
    }

    // MARK: - Package Plan

    private val minimalPackagePlanJson = """
        {
            "name": "1 GB / 7 Days",
            "price": 9.99,
            "data_GB": 1.0,
            "country": {
                "country_name": "Australia",
                "country_code": "AU",
                "country_flag": "flag.png",
                "country_flag_css": "au",
                "country_name_slug": "australia"
            },
            "currency": "USD",
            "currency_obj": {"symbol": "$", "iso": "USD"},
            "plan_type": "data",
            "kyc_display": "none",
            "package_slug": "au-1gb-7d",
            "validity_days": 7,
            "package_type_id": 42,
            "best_connectivity": "Telstra",
            "activation_policy": "first_use",
            "name_additional_text": ""
        }
    """

    @Test
    fun `PackagePlan decodes discount and promo fields`() {
        val payload = """
            {
                "name": "1 GB / 7 Days",
                "price": 9.99,
                "data_GB": 1.0,
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                },
                "currency": "USD",
                "currency_obj": {"symbol": "$", "iso": "USD"},
                "plan_type": "data",
                "kyc_display": "none",
                "package_slug": "au-1gb-7d",
                "validity_days": 7,
                "validity_days_display": "7 Days",
                "package_type_id": 42,
                "best_connectivity": "Telstra",
                "activation_policy": "first_use",
                "supported_countries": [],
                "name_additional_text": "",
                "discount_label": "SAVE 20%",
                "discount_percentage": "20.00",
                "promo_code": {
                    "valid": true,
                    "discount_code": "SUMMER",
                    "discount_percentage": 20.0,
                    "detail": "Summer sale"
                }
            }
        """.trimIndent()
        val plan = json.decodeFromString<PackagePlan>(payload)
        assertEquals("7 Days", plan.validityDaysDisplay)
        assertEquals("SAVE 20%", plan.discountLabel)
        assertEquals("20.00", plan.discountPercentage)
        assertEquals("SUMMER", plan.promoCode?.discount)
        assertTrue(plan.promoCode?.valid == true)
    }

    @Test
    fun `PackagePlan decodes payload without the new fields`() {
        val plan = json.decodeFromString<PackagePlan>(minimalPackagePlanJson.trimIndent())
        assertEquals("", plan.validityDaysDisplay)
        assertEquals("", plan.discountLabel)
        assertNull(plan.discountPercentage)
        assertNull(plan.promoCode)
        assertTrue(plan.supportedCountries.isEmpty())
    }

    // MARK: - Order History

    @Test
    fun `OrderHistoryItem decodes user conversion_tracked and purchase_country`() {
        val payload = """
            {
                "esim": {
                    "assigned_date": "2026-01-01",
                    "iccid": "8910",
                    "matching_id": "MATCH",
                    "premium": false,
                    "sm_dp_address": "sm-dp.example.com"
                },
                "order_number": 1001,
                "order_uuid": "uuid-1",
                "order_type": "BUY",
                "package_id": "pkg-1",
                "final_price": "9.99",
                "package_name": "1 GB / 7 Days",
                "purchase_date": "2026-01-01",
                "purchase_price": "9.99",
                "discount_code": "",
                "discount_amount": "0.00",
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "package_type_id": 42,
                "payment_status": "paid",
                "payment_method": "stripe_intent",
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                },
                "user": "user-1",
                "conversion_tracked": true,
                "purchase_country": {
                    "iso": "AU",
                    "name": "Australia",
                    "iso3": "AUS",
                    "flag": "flag.png",
                    "is_region": false
                }
            }
        """.trimIndent()
        val item = json.decodeFromString<OrderHistoryItem>(payload)
        assertEquals("user-1", item.user)
        assertTrue(item.conversionTracked)
        assertEquals("AU", item.purchaseCountry?.iso)
        assertFalse(item.purchaseCountry?.isRegion == true)
    }

    @Test
    fun `OrderHistoryItem decodes payload without the new fields`() {
        val payload = """
            {
                "esim": {
                    "assigned_date": "2026-01-01",
                    "iccid": "8910",
                    "matching_id": "MATCH",
                    "premium": false,
                    "sm_dp_address": "sm-dp.example.com"
                },
                "order_number": 1001,
                "order_uuid": "uuid-1",
                "order_type": "BUY",
                "package_id": "pkg-1",
                "final_price": "9.99",
                "package_name": "1 GB / 7 Days",
                "purchase_date": "2026-01-01",
                "purchase_price": "9.99",
                "discount_code": "",
                "discount_amount": "0.00",
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "package_type_id": 42,
                "payment_status": "paid",
                "payment_method": "stripe_intent",
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                }
            }
        """.trimIndent()
        val item = json.decodeFromString<OrderHistoryItem>(payload)
        assertEquals("", item.user)
        assertFalse(item.conversionTracked)
        assertNull(item.purchaseCountry)
    }

    // MARK: - Order Detail

    @Test
    fun `OrderDetail decodes nested package object`() {
        val payload = """
            {
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                },
                "customer_id": "u-1",
                "discount_amount": 0.0,
                "discount_code": "",
                "final_price": 9.99,
                "order_date": "2026-01-01",
                "order_number": 1001,
                "order_status": "COMPLETE",
                "order_type": "BUY",
                "package_data_size": 1.0,
                "package_type_id": 42,
                "package_name": "1 GB / 7 Days",
                "package_validity": 7,
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "purchase_price": 9.99,
                "payment_method": "stripe_intent",
                "package": ${minimalPackagePlanJson.trimIndent()}
            }
        """.trimIndent()
        val detail = json.decodeFromString<OrderDetail>(payload)
        assertNotNull(detail.packageInfo)
        assertEquals("au-1gb-7d", detail.packageInfo?.packageSlug)
        assertEquals(42L, detail.packageInfo?.packageTypeId)
    }

    @Test
    fun `OrderDetail decodes payload without a package object`() {
        val payload = """
            {
                "country": {
                    "country_name": "Australia",
                    "country_code": "AU",
                    "country_flag": "flag.png",
                    "country_flag_css": "au",
                    "country_name_slug": "australia"
                },
                "customer_id": "u-1",
                "discount_amount": 0.0,
                "discount_code": "",
                "final_price": 9.99,
                "order_date": "2026-01-01",
                "order_number": 1001,
                "order_status": "COMPLETE",
                "order_type": "BUY",
                "package_data_size": 1.0,
                "package_type_id": 42,
                "package_name": "1 GB / 7 Days",
                "package_validity": 7,
                "purchase_currency": "USD",
                "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
                "purchase_price": 9.99,
                "payment_method": "stripe_intent"
            }
        """.trimIndent()
        val detail = json.decodeFromString<OrderDetail>(payload)
        assertNull(detail.packageInfo)
        assertFalse(detail.tracked)
    }

    // MARK: - Payment Request

    @Test
    fun `PaymentRequest serializes coupon_id when set`() {
        val request = PaymentRequest(
            type = PaymentRequest.Type.BUY,
            customer = CustomerDetails(email = "a@b.com"),
            packageTypeId = 42,
            paymentMethod = PaymentRequest.Method.STRIPE_INTENT,
            autoTopUp = false,
            savePaymentMethod = false,
            couponId = "COUPON-1"
        )
        val encoded = json.encodeToString(PaymentRequest.serializer(), request)
        assertTrue(encoded.contains("\"coupon_id\":\"COUPON-1\""))
    }

    @Test
    fun `PaymentRequest omits coupon_id when unset`() {
        val request = PaymentRequest(
            type = PaymentRequest.Type.BUY,
            customer = CustomerDetails(email = "a@b.com"),
            packageTypeId = 42,
            paymentMethod = PaymentRequest.Method.STRIPE_INTENT,
            autoTopUp = false,
            savePaymentMethod = false
        )
        val encoded = json.encodeToString(PaymentRequest.serializer(), request)
        assertFalse(encoded.contains("coupon_id"))
    }

    // MARK: - Theme

    @Test
    fun `ThemeResponse decodes camelCase pages and destinations`() {
        val payload = """
            {
                "version": "2",
                "cdnBase": "https://cdn",
                "pages": {
                    "home": {
                        "urlPath": "/home",
                        "featuredImage": { "url": "https://cdn/home.png", "accent": "#123456" },
                        "color": "#FF0000"
                    }
                },
                "destinations": {
                    "south-africa": {
                        "image": { "url": "https://cdn/za.png" },
                        "gallery": ["https://cdn/za-1.png", "https://cdn/za-2.png"],
                        "countryCode": "za"
                    }
                }
            }
        """.trimIndent()
        val decoded = json.decodeFromString<ThemeResponse>(payload)
        assertEquals("2", decoded.version)
        assertEquals("https://cdn", decoded.cdnBase)
        assertEquals("/home", decoded.pages["home"]?.urlPath)
        assertEquals("#FF0000", decoded.pages["home"]?.color)
        assertEquals("https://cdn/home.png", decoded.pages["home"]?.featuredImage?.url)
        assertEquals("#123456", decoded.pages["home"]?.featuredImage?.accent)
        assertEquals("za", decoded.destinations["south-africa"]?.countryCode)
        assertEquals(2, decoded.destinations["south-africa"]?.gallery?.size)
    }

    @Test
    fun `ThemeResponse defaults pages and destinations to empty maps`() {
        val decoded = json.decodeFromString<ThemeResponse>("""{"version":"2"}""")
        assertTrue(decoded.pages.isEmpty())
        assertTrue(decoded.destinations.isEmpty())
        assertNull(decoded.cdnBase)
    }

    @Test
    fun `ThemePage decodes with only a color`() {
        val decoded = json.decodeFromString<ThemePage>("""{"color":"#00FF00"}""")
        assertEquals("#00FF00", decoded.color)
        assertNull(decoded.urlPath)
        assertNull(decoded.featuredImage)
    }

    @Test
    fun `ThemeDestination decodes without a gallery`() {
        val decoded = json.decodeFromString<ThemeDestination>(
            """{"image":{"url":"https://cdn/za.png"},"countryCode":"ZA"}"""
        )
        assertEquals("https://cdn/za.png", decoded.image?.url)
        assertNull(decoded.image?.accent)
        assertNull(decoded.gallery)
        assertEquals("ZA", decoded.countryCode)
    }

    // MARK: - Destination FAQs

    @Test
    fun `DestinationFaqResponse decodes slug name language and faqs`() {
        val payload = """
            {
                "slug": "south-africa",
                "name": "South Africa",
                "language": "en",
                "faqs": [
                    { "question": "Does it work?", "answer": "Yes." },
                    { "question": "How much?", "answer": "Ten." }
                ]
            }
        """.trimIndent()
        val decoded = json.decodeFromString<DestinationFaqResponse>(payload)
        assertEquals("south-africa", decoded.slug)
        assertEquals("South Africa", decoded.name)
        assertEquals("en", decoded.language)
        assertEquals(2, decoded.faqs.size)
        assertEquals("Does it work?", decoded.faqs.first().question)
        assertEquals("Yes.", decoded.faqs.first().answer)
    }

    @Test
    fun `DestinationFaqResponse decodes an empty faqs list`() {
        val payload = """{"slug":"za","name":"South Africa","language":"en","faqs":[]}"""
        val decoded = json.decodeFromString<DestinationFaqResponse>(payload)
        assertTrue(decoded.faqs.isEmpty())
    }

    @Test
    fun `Faq decodes question and answer`() {
        val decoded = json.decodeFromString<Faq>("""{"question":"Q","answer":"A"}""")
        assertEquals("Q", decoded.question)
        assertEquals("A", decoded.answer)
    }

    // MARK: - Store review

    @Test
    fun `RatingApiResponse decodes reviews stats and the summary together`() {
        val payload = """
            {
                "store_name": "KnowRoaming",
                "review_count": 1000,
                "results_count": 980,
                "verdict": "Excellent",
                "average_rating": 4.5,
                "reviews": [
                    {
                        "type": "store_review",
                        "type_label": "Store review",
                        "rating": 5,
                        "title": "Great",
                        "comments": "Worked well",
                        "author": { "name": "Alice", "location": "Cape Town" },
                        "date_created": "2026-01-01",
                        "time_ago": "2 days ago",
                        "sku": "SKU-1"
                    }
                ],
                "stats": {
                    "company": { "review_count": 1000, "average_rating": "4.5" },
                    "ratings": { "4": 200, "5": 800 }
                }
            }
        """.trimIndent()
        val decoded = json.decodeFromString<RatingApiResponse>(payload)
        assertEquals("KnowRoaming", decoded.storeName)
        assertEquals(1000, decoded.reviewCount)
        assertEquals(980, decoded.resultsCount)
        assertEquals("Excellent", decoded.verdict)
        assertEquals(4.5, decoded.rating!!, 0.001)
        assertEquals(1, decoded.reviews?.size)
        assertEquals("store_review", decoded.reviews?.first()?.type)
        assertEquals("Store review", decoded.reviews?.first()?.typeLabel)
        assertEquals(5, decoded.reviews?.first()?.rating)
        assertEquals("2026-01-01", decoded.reviews?.first()?.dateCreated)
        assertEquals("2 days ago", decoded.reviews?.first()?.timeAgo)
        assertEquals("SKU-1", decoded.reviews?.first()?.sku)
        assertEquals("Alice", decoded.reviews?.first()?.author?.name)
        assertEquals("Cape Town", decoded.reviews?.first()?.author?.location)
        assertEquals(1000, decoded.stats?.company?.reviewCount)
        assertEquals("4.5", decoded.stats?.company?.averageRating)
        assertEquals(200, decoded.stats?.ratings?.four)
        assertEquals(800, decoded.stats?.ratings?.five)
    }

    @Test
    fun `RatingApiResponse decodes a summary only payload with null reviews and stats`() {
        val payload = """
            {
                "store_name": "KnowRoaming",
                "review_count": 12,
                "results_count": 12,
                "verdict": "Good",
                "average_rating": 4.0
            }
        """.trimIndent()
        val decoded = json.decodeFromString<RatingApiResponse>(payload)
        assertEquals("Good", decoded.verdict)
        assertNull(decoded.reviews)
        assertNull(decoded.stats)
    }

    @Test
    fun `Review decodes with only a rating`() {
        val decoded = json.decodeFromString<Review>("""{"rating":3}""")
        assertEquals(3, decoded.rating)
        assertNull(decoded.author)
        assertNull(decoded.comments)
    }

    @Test
    fun `Stats decodes ratings keyed by numeric strings`() {
        val decoded = json.decodeFromString<Stats>("""{"ratings":{"4":1,"5":2}}""")
        assertNull(decoded.company)
        assertEquals(1, decoded.ratings?.four)
        assertEquals(2, decoded.ratings?.five)
    }

    // MARK: - Null tolerance regressions

    @Test
    fun `OrderHistoryItem decodes an explicit null country`() {
        val payload = orderHistoryJson(country = "null")
        val item = productionJson.decodeFromString<OrderHistoryItem>(payload)
        assertNull(item.country)
        assertEquals(1001, item.orderNumber)
        assertEquals("uuid-1", item.orderUUID)
    }

    @Test
    fun `OrderHistoryItem decodes an absent country`() {
        val item = productionJson.decodeFromString<OrderHistoryItem>(orderHistoryJson(country = null))
        assertNull(item.country)
        assertEquals(1001, item.orderNumber)
    }

    @Test
    fun `an order history page survives one row with a null country`() {
        val payload = """
            [${orderHistoryJson(country = "null")},${orderHistoryJson()}]
        """.trimIndent()
        val items = productionJson.decodeFromString<List<OrderHistoryItem>>(payload)
        assertEquals(2, items.size)
        assertNull(items[0].country)
        assertEquals("AU", items[1].country?.code)
    }

    @Test
    fun `OrderDetail decodes an explicit null country`() {
        val detail = productionJson.decodeFromString<OrderDetail>(orderDetailJson(country = "null"))
        assertNull(detail.country)
        assertEquals(1001, detail.orderNumber)
        assertEquals("COMPLETE", detail.orderStatus)
    }

    @Test
    fun `OrderDetail decodes an absent country`() {
        val detail = productionJson.decodeFromString<OrderDetail>(orderDetailJson(country = null))
        assertNull(detail.country)
        assertEquals(1001, detail.orderNumber)
    }

    @Test
    fun `OrderDetail decodes a null payment_method as unknown`() {
        val payload = orderDetailJson(paymentMethod = "null")
        val detail = productionJson.decodeFromString<OrderDetail>(payload)
        assertEquals(PaymentMethod.UNKNOWN, detail.paymentMethod)
    }

    @Test
    fun `OrderHistoryItem decodes a null payment_method as unknown`() {
        val item = productionJson.decodeFromString<OrderHistoryItem>(orderHistoryJson(paymentMethod = "null"))
        assertEquals(PaymentMethod.UNKNOWN, item.paymentMethod)
    }

    @Test
    fun `Country decodes an object whose every string is null`() {
        val payload = """
            {
                "country_name": null,
                "country_code": null,
                "country_flag": null,
                "country_flag_css": null,
                "country_name_slug": null
            }
        """.trimIndent()
        val country = productionJson.decodeFromString<Country>(payload)
        assertEquals("", country.name)
        assertEquals("", country.code)
        assertEquals("", country.flag)
        assertEquals("", country.flagCss)
        assertEquals("", country.slug)
        assertFalse(country.isGlobal)
    }

    @Test
    fun `CurrencyObject decodes a null symbol and iso`() {
        val currency = productionJson.decodeFromString<CurrencyObject>("""{"symbol":null,"iso":null}""")
        assertEquals("", currency.symbol)
        assertEquals("", currency.isoCode)
    }

    @Test
    fun `EsimInfo decodes a payload of nulls`() {
        val payload = """
            {
                "assigned_date": null,
                "iccid": null,
                "matching_id": null,
                "premium": null,
                "sm_dp_address": null
            }
        """.trimIndent()
        val esim = productionJson.decodeFromString<EsimInfo>(payload)
        assertEquals("", esim.iccid)
        assertEquals("", esim.matchingId)
        assertEquals("", esim.smDpAddress)
        assertEquals("", esim.assignedDate)
        assertFalse(esim.premium)
    }

    @Test
    fun `OrderHistoryItem decodes a null esim`() {
        val item = productionJson.decodeFromString<OrderHistoryItem>(orderHistoryJson(esim = "null"))
        assertEquals("", item.esim.iccid)
        assertEquals(1001, item.orderNumber)
    }

    // MARK: - Lenient from_price

    @Test
    fun `Country decodes a numeric from_price as its literal text`() {
        val country = productionJson.decodeFromString<Country>("""{"country_name":"AU","from_price":4.99}""")
        assertEquals("4.99", country.fromPrice)
        assertEquals(4.99, country.fromPriceValue!!, 0.0001)
    }

    @Test
    fun `Country keeps a quoted from_price verbatim`() {
        val country = productionJson.decodeFromString<Country>("""{"country_name":"AU","from_price":"4.99"}""")
        assertEquals("4.99", country.fromPrice)
        assertEquals(4.99, country.fromPriceValue!!, 0.0001)
    }

    @Test
    fun `Country decodes a null from_price`() {
        val country = productionJson.decodeFromString<Country>("""{"country_name":"AU","from_price":null}""")
        assertNull(country.fromPrice)
        assertNull(country.fromPriceValue)
    }

    @Test
    fun `Country keeps an unparseable from_price but has no value for it`() {
        val country = productionJson.decodeFromString<Country>("""{"country_name":"AU","from_price":"N/A"}""")
        assertEquals("N/A", country.fromPrice)
        assertNull(country.fromPriceValue)
    }

    @Test
    fun `Country decodes an absent from_price`() {
        val country = productionJson.decodeFromString<Country>("""{"country_name":"AU"}""")
        assertNull(country.fromPrice)
        assertNull(country.fromPriceValue)
    }

    // MARK: - Loyalty points currency

    @Test
    fun `LoyaltyPointsDetail decodes an explicit null currency`() {
        val payload = """{"amount":"15.00","currency":null}"""
        val detail = productionJson.decodeFromString<LoyaltyPointsDetail>(payload)
        assertEquals("15.00", detail.resolvedAmount)
        assertEquals("", detail.resolvedCurrencyIso)
    }

    @Test
    fun `LoyaltyPointsDetail decodes an absent currency`() {
        val detail = productionJson.decodeFromString<LoyaltyPointsDetail>("""{"amount":"15.00"}""")
        assertEquals("15.00", detail.resolvedAmount)
        assertEquals("", detail.resolvedCurrencyIso)
    }

    @Test
    fun `KredsLoyaltyBalanceResponse decodes a null detail`() {
        val payload = """{"total_loyalty_points":null,"total_loyalty_points_detail":null}"""
        val balance = productionJson.decodeFromString<KredsLoyaltyBalanceResponse>(payload)
        assertEquals(0, balance.totalLoyaltyPoints)
        assertEquals("0.00", balance.totalLoyaltyPointsDetail.resolvedAmount)
    }

    // MARK: - Tolerant package country

    @Test
    fun `PackagePlan decodes a bare string country like iOS`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(country = "\"Japan\""))
        assertEquals("Japan", plan.country.name)
        assertEquals("", plan.country.code)
    }

    @Test
    fun `PackagePlan decodes a null country`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(country = "null"))
        assertEquals("", plan.country.name)
    }

    @Test
    fun `PackagePlan decodes an object country`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson())
        assertEquals("Australia", plan.country.name)
        assertEquals("AU", plan.country.code)
    }

    // MARK: - Money strings

    @Test
    fun `PackagePlan keeps a string price verbatim and exposes its value`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "\"899.00\""))
        assertEquals("899.00", plan.price)
        assertEquals(899.0, plan.priceValue, 0.0001)
    }

    @Test
    fun `PackagePlan keeps a zero-decimal string price verbatim`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "\"1500\""))
        assertEquals("1500", plan.price)
        assertEquals(1500.0, plan.priceValue, 0.0001)
    }

    @Test
    fun `PackagePlan decodes a bare decimal price as its literal text`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "9.99"))
        assertEquals("9.99", plan.price)
        assertEquals(9.99, plan.priceValue, 0.0001)
    }

    @Test
    fun `PackagePlan decodes a bare integer price as its literal text`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "1500"))
        assertEquals("1500", plan.price)
        assertEquals(1500.0, plan.priceValue, 0.0001)
    }

    @Test
    fun `PackagePlan without a discounted price purchases at the list price`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "\"899.00\""))
        assertNull(plan.discountedPrice)
        assertNull(plan.discountedPriceValue)
        assertEquals("899.00", plan.purchasePrice)
        assertEquals(899.0, plan.purchasePriceValue, 0.0001)
        assertFalse(plan.hasDiscount)
        assertFalse(plan.isFreePurchase)
    }

    @Test
    fun `PackagePlan with a string discounted price purchases at the discount`() {
        val plan = productionJson.decodeFromString<PackagePlan>(
            packagePlanJson(price = "\"899.00\"", extra = """, "discounted_price": "539.00""""),
        )
        assertEquals("539.00", plan.discountedPrice)
        assertEquals(539.0, plan.discountedPriceValue!!, 0.0001)
        assertEquals("539.00", plan.purchasePrice)
        assertEquals(539.0, plan.purchasePriceValue, 0.0001)
        assertTrue(plan.hasDiscount)
        assertFalse(plan.isFreePurchase)
    }

    @Test
    fun `PackagePlan with a bare numeric discounted price purchases at the discount`() {
        val plan = productionJson.decodeFromString<PackagePlan>(
            packagePlanJson(price = "9.99", extra = """, "discounted_price": 4.99"""),
        )
        assertEquals("4.99", plan.discountedPrice)
        assertEquals("4.99", plan.purchasePrice)
        assertEquals(4.99, plan.purchasePriceValue, 0.0001)
        assertTrue(plan.hasDiscount)
    }

    @Test
    fun `PackagePlan with a zero discounted price is free and reports no discount`() {
        val plan = productionJson.decodeFromString<PackagePlan>(
            packagePlanJson(price = "\"899.00\"", extra = """, "discounted_price": "0.00""""),
        )
        assertEquals("0.00", plan.purchasePrice)
        assertTrue(plan.isFreePurchase)
        assertFalse(plan.hasDiscount)
    }

    @Test
    fun `PackagePlan with a null discounted price has no discount`() {
        val plan = productionJson.decodeFromString<PackagePlan>(
            packagePlanJson(price = "\"899.00\"", extra = """, "discounted_price": null"""),
        )
        assertNull(plan.discountedPrice)
        assertFalse(plan.hasDiscount)
        assertEquals("899.00", plan.purchasePrice)
    }

    @Test
    fun `PackagePlan with a zero list price is free`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "\"0.00\""))
        assertTrue(plan.isFreePurchase)
        assertFalse(plan.hasDiscount)
    }

    @Test
    fun `PackagePlan keeps an unparseable price but values it at zero`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "\"N/A\""))
        assertEquals("N/A", plan.price)
        assertEquals(0.0, plan.priceValue, 0.0)
    }

    @Test
    fun `PackagePlan leaves converted_price as a number`() {
        val plan = productionJson.decodeFromString<PackagePlan>(
            packagePlanJson(price = "\"899.00\"", extra = """, "converted_price": 5.75"""),
        )
        assertEquals(5.75, plan.convertedPrice!!, 0.0001)
    }

    @Test
    fun `PackagePlan does not serialize its computed money properties`() {
        val plan = productionJson.decodeFromString<PackagePlan>(packagePlanJson(price = "\"899.00\""))
        val encoded = productionJson.encodeToString(plan)
        assertTrue(encoded.contains("\"price\":\"899.00\""))
        assertFalse(encoded.contains("purchasePrice"))
        assertFalse(encoded.contains("priceValue"))
        assertFalse(encoded.contains("hasDiscount"))
        assertFalse(encoded.contains("isFreePurchase"))
    }

    @Test
    fun `OrderDetail keeps string money fields verbatim and exposes their values`() {
        val detail = productionJson.decodeFromString<OrderDetail>(
            orderDetailJson(discountAmount = "\"360.00\"", finalPrice = "\"539.00\"", purchasePrice = "\"899.00\""),
        )
        assertEquals("360.00", detail.discountAmount)
        assertEquals(360.0, detail.discountAmountValue, 0.0001)
        assertEquals("539.00", detail.finalPrice)
        assertEquals(539.0, detail.finalPriceValue, 0.0001)
        assertEquals("899.00", detail.price)
        assertEquals(899.0, detail.priceValue, 0.0001)
    }

    @Test
    fun `OrderDetail decodes bare numeric money fields as their literal text`() {
        val detail = productionJson.decodeFromString<OrderDetail>(
            orderDetailJson(discountAmount = "0.0", finalPrice = "9.99", purchasePrice = "3249"),
        )
        assertEquals("0.0", detail.discountAmount)
        assertEquals(0.0, detail.discountAmountValue, 0.0)
        assertEquals("9.99", detail.finalPrice)
        assertEquals(9.99, detail.finalPriceValue, 0.0001)
        assertEquals("3249", detail.price)
        assertEquals(3249.0, detail.priceValue, 0.0001)
    }

    @Test
    fun `OrderDetail still fails as a whole when a money field is null`() {
        assertThrows(SerializationException::class.java) {
            productionJson.decodeFromString<OrderDetail>(orderDetailJson(finalPrice = "null"))
        }
    }

    // MARK: - Lenient string serializer

    @Serializable
    private data class LenientHolder(
        @Serializable(with = LenientStringSerializer::class) val value: String,
    )

    @Test
    fun `LenientStringSerializer keeps a JSON string verbatim`() {
        assertEquals("12.50", productionJson.decodeFromString<LenientHolder>("""{"value":"12.50"}""").value)
        assertEquals("", productionJson.decodeFromString<LenientHolder>("""{"value":""}""").value)
    }

    @Test
    fun `LenientStringSerializer returns the literal text of a JSON number`() {
        assertEquals("12.5", productionJson.decodeFromString<LenientHolder>("""{"value":12.5}""").value)
        assertEquals("1500", productionJson.decodeFromString<LenientHolder>("""{"value":1500}""").value)
        assertEquals("-3", productionJson.decodeFromString<LenientHolder>("""{"value":-3}""").value)
        assertEquals("1e3", productionJson.decodeFromString<LenientHolder>("""{"value":1e3}""").value)
    }

    @Test
    fun `LenientStringSerializer rejects null objects and arrays`() {
        assertThrows(SerializationException::class.java) {
            productionJson.decodeFromString<LenientHolder>("""{"value":null}""")
        }
        assertThrows(SerializationException::class.java) {
            productionJson.decodeFromString<LenientHolder>("""{"value":{"amount":1}}""")
        }
        assertThrows(SerializationException::class.java) {
            productionJson.decodeFromString<LenientHolder>("""{"value":[1]}""")
        }
    }

    @Test
    fun `LenientStringSerializer encodes as a JSON string`() {
        assertEquals("""{"value":"9.99"}""", productionJson.encodeToString(LenientHolder("9.99")))
    }

    // MARK: - Null tolerance fixtures

    private fun packagePlanJson(
        country: String = countryObjectJson,
        price: String = "9.99",
        extra: String = "",
    ): String = """
        {
            "name": "1 GB / 7 Days",
            "price": $price,
            "data_GB": 1.0,
            "country": $country,
            "currency": "USD",
            "currency_obj": {"symbol": "$", "iso": "USD"},
            "plan_type": "data",
            "kyc_display": "none",
            "package_slug": "au-1gb-7d",
            "validity_days": 7,
            "package_type_id": 42,
            "best_connectivity": "Telstra",
            "activation_policy": "first_use",
            "name_additional_text": ""
            $extra
        }
    """.trimIndent()

    private fun orderHistoryJson(
        country: String? = countryObjectJson,
        paymentMethod: String = "\"stripe_intent\"",
        esim: String = esimInfoJson,
    ): String = """
        {
            "esim": $esim,
            "order_number": 1001,
            "order_uuid": "uuid-1",
            "order_type": "BUY",
            "package_id": "pkg-1",
            "final_price": "9.99",
            "package_name": "1 GB / 7 Days",
            "purchase_date": "2026-01-01",
            "purchase_price": "9.99",
            "discount_code": "",
            "discount_amount": "0.00",
            "purchase_currency": "USD",
            "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
            "package_type_id": 42,
            "payment_status": "paid",
            "payment_method": $paymentMethod
            ${if (country == null) "" else ", \"country\": $country"}
        }
    """.trimIndent()

    private fun orderDetailJson(
        country: String? = countryObjectJson,
        paymentMethod: String = "\"stripe_intent\"",
        discountAmount: String = "0.0",
        finalPrice: String = "9.99",
        purchasePrice: String = "9.99",
    ): String = """
        {
            "customer_id": "u-1",
            "discount_amount": $discountAmount,
            "discount_code": "",
            "final_price": $finalPrice,
            "order_date": "2026-01-01",
            "order_number": 1001,
            "order_status": "COMPLETE",
            "order_type": "BUY",
            "package_data_size": 1.0,
            "package_type_id": 42,
            "package_name": "1 GB / 7 Days",
            "package_validity": 7,
            "purchase_currency": "USD",
            "purchase_currency_obj": {"symbol": "$", "iso": "USD"},
            "purchase_price": $purchasePrice,
            "payment_method": $paymentMethod
            ${if (country == null) "" else ", \"country\": $country"}
        }
    """.trimIndent()

    // MARK: - Customer live payload parity

    @Test
    fun `Customer decodes unique_referral_code as the referral code`() {
        val payload = """
            {
                "customer_id": "u-1",
                "unique_referral_code": "UNIQUE99"
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("UNIQUE99", customer.referralCode)
    }

    @Test
    fun `Customer decodes referral_code as the referral code`() {
        val payload = """
            {
                "customer_id": "u-1",
                "referral_code": "PLAIN42"
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("PLAIN42", customer.referralCode)
    }

    @Test
    fun `Customer prefers a present alias over an absent referral code`() {
        val payload = """
            {
                "customer_id": "u-1",
                "referral_code": null,
                "unique_referral_code": "PRESENT2"
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("PRESENT2", customer.referralCode)
    }

    @Test
    fun `Customer leaves the referral code null when neither key is present`() {
        val customer = json.decodeFromString<Customer>("""{"customer_id": "u-1"}""")
        assertNull(customer.referralCode)
    }

    @Test
    fun `Customer encodes the referral code as referral_code only`() {
        val encoded = productionJson.encodeToString(Customer(id = "u-1", referralCode = "ROUND1"))
        assertTrue(encoded.contains("\"referral_code\":\"ROUND1\""))
        assertFalse(encoded.contains("unique_referral_code"))
        assertEquals("ROUND1", productionJson.decodeFromString<Customer>(encoded).referralCode)
    }

    @Test
    fun `Customer decodes acquisition_source`() {
        val payload = """
            {
                "customer_id": "u-1",
                "acquisition_source": "referral"
            }
        """.trimIndent()
        assertEquals("referral", json.decodeFromString<Customer>(payload).acquisitionSource)
    }

    @Test
    fun `Customer decodes all eight live notification flags`() {
        val payload = """
            {
                "customer_id": "u-1",
                "receive_marketing_email": true,
                "receive_marketing_push": false,
                "receive_account_email": true,
                "receive_account_sms": false,
                "receive_account_push": true,
                "receive_purchase_email": false,
                "receive_purchase_push": true,
                "receive_viber_messages": false
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals(true, customer.receiveMarketingEmail)
        assertEquals(false, customer.receiveMarketingPush)
        assertEquals(true, customer.receiveAccountEmail)
        assertEquals(false, customer.receiveAccountSms)
        assertEquals(true, customer.receiveAccountPush)
        assertEquals(false, customer.receivePurchaseEmail)
        assertEquals(true, customer.receivePurchasePush)
        assertEquals(false, customer.receiveViberMessages)
    }

    @Test
    fun `Customer decodes when the live notification flags are absent`() {
        val customer = json.decodeFromString<Customer>("""{"customer_id": "u-1"}""")
        assertNull(customer.receiveMarketingEmail)
        assertNull(customer.receiveMarketingPush)
        assertNull(customer.receiveAccountEmail)
        assertNull(customer.receiveAccountSms)
        assertNull(customer.receiveAccountPush)
        assertNull(customer.receivePurchaseEmail)
        assertNull(customer.receivePurchasePush)
        assertNull(customer.receiveViberMessages)
    }

    @Test
    fun `Customer decodes the live preferences payload without losing the referral code`() {
        val payload = """
            {
                "customer_id": "u-1",
                "unique_referral_code": "PREFS7",
                "acquisition_source": "organic",
                "loyalty_provider": "mokafaa",
                "preferred_language": "ar",
                "preferred_currency": "SAR"
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals("PREFS7", customer.referralCode)
        assertEquals("organic", customer.acquisitionSource)
        assertEquals("mokafaa", customer.loyaltyProvider)
        assertNull(customer.signedInWithProvider)
    }

}

private val countryObjectJson = """
    {
        "country_name": "Australia",
        "country_code": "AU",
        "country_flag": "flag.png",
        "country_flag_css": "au",
        "country_name_slug": "australia"
    }
""".trimIndent()

private val esimInfoJson = """
    {
        "assigned_date": "2026-01-01",
        "iccid": "8910",
        "matching_id": "MATCH",
        "premium": false,
        "sm_dp_address": "sm-dp.example.com"
    }
""".trimIndent()

