package io.esimplified.sdk.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    // MARK: - Notification Preferences

    @Test
    fun `Customer decodes receive_emails receive_push_notifications and receive_sms`() {
        val payload = """
            {
                "customer_id": "u-1",
                "receive_emails": false,
                "receive_push_notifications": false,
                "receive_sms": false
            }
        """.trimIndent()
        val customer = json.decodeFromString<Customer>(payload)
        assertEquals(false, customer.receiveEmails)
        assertEquals(false, customer.receivePushNotifications)
        assertEquals(false, customer.receiveSms)
    }

    @Test
    fun `Customer defaults notification preferences to true when absent`() {
        val customer = json.decodeFromString<Customer>("""{"customer_id": "u-1"}""")
        assertEquals(true, customer.receiveEmails)
        assertEquals(true, customer.receivePushNotifications)
        assertEquals(true, customer.receiveSms)
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
}
