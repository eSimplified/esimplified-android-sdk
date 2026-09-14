# eSIMplified Android SDK

[![CI](https://github.com/eSimplified/esimplified-android-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/eSimplified/esimplified-android-sdk/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.esimplified/android-sdk)](https://central.sonatype.com/artifact/io.github.esimplified/android-sdk)
[![API](https://img.shields.io/badge/API-28%2B-brightgreen)](https://android-arsenal.com/api?level=28)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.20-blue)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Proprietary-blue)](LICENSE)

Kotlin SDK for integrating the eSIMplified eSIM platform into Android applications. Provides typed repository interfaces for authentication, eSIM management, package browsing, orders, payments, and more. All networking, authentication, and token management are handled internally -- consuming apps interact only with clean Kotlin interfaces.

**Coordinates:** `io.github.esimplified:android-sdk:2.0.0`

## Requirements

- Android `minSdk 28` (Android 9)
- Kotlin 2.x
- [Koin](https://insert-koin.io/) for dependency injection

## Prerequisites

To use the SDK you need credentials issued by eSimplified:

- **`clientName`** — your registered brand identifier (used to build your API base URL)
- **`clientId`** — your OAuth2 client ID
- **`clientSecret`** — your OAuth2 client secret
- **`awsWafToken`** — your AWS WAF validation token

Contact eSimplified to obtain these before integrating. See [Support](#support) below.

## Installation

The SDK is published to Maven Central. No extra repositories or authentication needed.

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("io.github.esimplified:android-sdk:2.0.0")
}
```

Maven Central is included by default in all Gradle projects. No changes to `settings.gradle.kts` required.

> **Upgrading from 1.x?** 2.0 changes the type of every money field, makes several model fields nullable, and removes seven unused request types. Read [Migrating from 1.x to 2.0](#migrating-from-1x-to-20) before you bump the version.

## Quick Start

### 1. Initialize the SDK

Call this once in your `Application.onCreate()`.

> ⚠️ **Do not ship `clientId`, `clientSecret`, or `awsWafToken` as string literals in your app code.** They're trivially extractable from a shipped APK. Fetch them at runtime from a server-controlled source — Firebase Remote Config is the recommended pattern, so you can rotate credentials without publishing a new app version.

```kotlin
// Fetch credentials from Firebase Remote Config at launch
val remoteConfig = Firebase.remoteConfig
remoteConfig.fetchAndActivate().await()

EsimplifiedSdk.initialize(
    context = this,
    config = SdkConfig(
        environment = SdkEnvironment.PRODUCTION,                    // or STAGING / TESTING
        clientName = "yourcompany",                                 // your registered brand name
        clientId = remoteConfig.getString("client_id"),             // OAuth2 client ID
        clientSecret = remoteConfig.getString("client_secret"),     // OAuth2 client secret
        awsWafToken = remoteConfig.getString("x_auth_validation"),  // AWS WAF validation token
        enableLogging = BuildConfig.DEBUG,                          // never enable in production
    )
)
```

The SDK constructs API URLs automatically from `clientName` and `environment`:
- **Staging:** `https://{clientName}.stage.esimplified.io`
- **Testing:** `https://{clientName}.test.esimplified.io`
- **Production:** `https://{clientName}.live.esimplified.io`

**About the testing environment:** functionally identical to staging, but token lifetimes are drastically shorter — access tokens expire after **120 seconds** (measured via `expires_in`), versus hours on staging/production. Because the SDK proactively refreshes any token within 5 minutes of expiry, a testing build refreshes on effectively every request. Combined with the backend's single-use refresh-token rotation, this surfaces token-refresh and session-persistence bugs in seconds that would otherwise take a day of device idle time to reproduce. Use TESTING to verify auth flows (login, biometric re-login, cold-start refresh, logout); use STAGING for everything else.

If you can't use Remote Config, fetch from your own backend at launch. Avoid persisting these values long-term on device.

### 2. Load the Koin module

```kotlin
startKoin {
    modules(
        EsimplifiedSdk.koinModule(),
        // your app modules...
    )
}
```

### 3. Inject and use repositories

```kotlin
class StoreViewModel(
    private val countryRepo: CountryRepository,
    private val packagesRepo: PackagesRepository,
) : ViewModel() {

    fun loadPackages(countryCode: String) = viewModelScope.launch {
        val countries = countryRepo.getCountries()
        val packages = packagesRepo.getPackages(
            Destination(code = countryCode)
        )
    }
}
```

## Common Recipe — Buy an eSIM

End-to-end flow for purchasing an eSIM package:

```kotlin
// 1. Browse destinations
val countries = countryRepo.getCountries()

// 2. Show packages for selected country
val packages = packagesRepo.getPackages(Destination(code = "US"))

// 3. Authenticate the customer
authRepo.login(email = email, password = password)

// 4. Create a Stripe payment intent
val payment = paymentsRepo.getPaymentIntent(
    PaymentRequest(
        type = "buy",
        iccid = null,
        customer = customerDetails,
        packageTypeId = packages.first().packageTypeId.toInt(),
        paymentMethod = "stripe_intent",
        autoTopUp = false,
        savePaymentMethod = true,
        loyaltyPointsAmount = null,
    )
)
// payment.transaction?.uri → Stripe client secret. Confirm via Stripe Android SDK.

// 5. Once Stripe confirms, fetch the order to get the eSIM QR code
val orderUUID = payment.transaction?.orderId ?: return
val order = ordersRepo.getOrderDetails(orderUUID = orderUUID)
// order.qrCode / order.qrCodeImageBase64 / order.activationCode

// 6. Confirm conversion tracking
ordersRepo.trackOrder(orderUuid = orderUUID)
```

## SdkConfig

```kotlin
SdkConfig(
    environment: SdkEnvironment,                         // STAGING, TESTING or PRODUCTION
    clientName: String,                                  // your brand name (used to build API URL)
    apiVersion: String = "v2",                           // API version path segment
    clientId: String,                                    // OAuth2 client ID
    clientSecret: String,                                // OAuth2 client secret
    awsWafToken: String = "",                            // AWS WAF validation token
    enableLogging: Boolean = false,                      // enable OkHttp request/response logging
    customHeadersProvider: (() -> Map<String, String>)?, // optional extra headers per request
    enableCaching: Boolean = true,                       // in-memory response cache (see Caching)
    defaultCacheTtlSeconds: Long = 3600,                 // fallback TTL when a call doesn't pass one
)
```

Setting `enableCaching = false` gives every cache entry a zero TTL, so every read goes to the network. Per-call `cacheTTL` arguments still apply when caching is on.

## SDK Structure

```
sdk/src/main/java/io/esimplified/sdk/
|-- EsimplifiedSdk.kt                    # SDK entry point (initialize, koinModule)
|-- SdkConfig.kt                          # Configuration data class
|-- SdkEnvironment.kt                     # STAGING / TESTING / PRODUCTION enum
|-- auth/
|   |-- Auth.kt                           # Sealed interface: Unauthenticated | Authenticated
|   |-- SessionManager.kt                 # Session state interface
|   |-- DefaultSessionManager.kt          # Default implementation (EncryptedSharedPreferences)
|   |-- SecureStorageProvider.kt           # Storage abstraction interface
|   |-- DefaultSecureStorage.kt           # AES-256 EncryptedSharedPreferences implementation
|   +-- TokenProvider.kt                  # Token access/refresh interface
|-- network/
|   |-- ApiService.kt                     # Retrofit API endpoint definitions
|   |-- BaseResponse.kt                   # Paginated response wrapper
|   |-- ApiErrorMessage.kt                # Shared API error-body parser
|   |-- SdkCache.kt                       # Internal keyed in-memory cache
|   |-- SdkError.kt                       # Public error type (sealed, extends IOException)
|   +-- SdkAuthInterceptor.kt            # OkHttp interceptor for auth + token refresh
|-- model/                                # All API data models (see table below)
|-- repository/                           # Public repository interfaces
|   |-- AuthRepository.kt
|   |-- CountryRepository.kt
|   |-- PackagesRepository.kt
|   |-- EsimRepository.kt
|   |-- OrdersRepository.kt
|   |-- PaymentsRepository.kt
|   |-- PromoCodeRepository.kt
|   |-- LoyaltyRepository.kt
|   |-- UserRepository.kt
|   |-- NotificationRepository.kt
|   |-- VisaRewardsRepository.kt
|   |-- VouchersRepository.kt
|   |-- ThemeRepository.kt
|   |-- FaqAndSupportRepository.kt
|   |-- StoreReviewRepository.kt
|   |-- RepositoryResult.kt               # Value + isStale + failure wrapper
|   +-- impl/                             # Internal implementations (not public API)
+-- di/
    +-- SdkModule.kt                      # Koin module wiring all dependencies
```

## Money fields

Every money amount the API returns is exposed as a **`String`**, holding the server's decimal text verbatim (`"12.50"`). This matches the eSIMplified iOS SDK field for field, and it means a price you render is exactly the price the server sent — no float rounding, no locale drift.

Each money field has a companion `…Value: Double` accessor for arithmetic:

```kotlin
Text("${plan.currencyObject.symbol}${plan.purchasePrice}")   // display: use the String
val total = plan.purchasePriceValue * quantity                // arithmetic: use the …Value
```

| Model | String field | `Double` accessor |
|---|---|---|
| `PackagePlan` | `price` | `priceValue: Double` |
| `PackagePlan` | `discountedPrice: String?` | `discountedPriceValue: Double?` |
| `PackagePlan` | `purchasePrice` (computed: `discountedPrice ?: price`) | `purchasePriceValue: Double` |
| `OrderDetail` | `price` (`purchase_price`) | `priceValue: Double` |
| `OrderDetail` | `finalPrice` | `finalPriceValue: Double` |
| `OrderDetail` | `discountAmount` | `discountAmountValue: Double` |
| `Country` | `fromPrice: String?` | `fromPriceValue: Double?` |

Decoding accepts either a JSON string (`"12.50"`) or a bare number (`12.5`) for these fields, so a backend that changes its mind about quoting will not break the client.

`PackagePlan.convertedPrice` stays `Double?` — it is a `Double?` in the iOS SDK too. `OrderHistoryItem.finalPrice` / `purchasePrice` / `discountAmount` were already `String` before 2.0 and are unchanged; they have no `…Value` accessors, and neither does the iOS SDK's order-history model.

## All Models

Every model is a `@Serializable` data class in `io.esimplified.sdk.model`.

| Model | Description |
|---|---|
| `Customer` | Authenticated user profile (id, email, name, phone, wallet, referral code, acquisition source, notification flags) |
| `CustomerDetails` | Mutable customer fields for registration and profile updates |
| `CustomerForgetPassword` | Forgot password request (email) |
| `CustomerForgetPasswordResponse` | Forgot password API response |
| `CustomerChangePassword` | Change/reset password request payload |
| `Country` | Destination country (name, code, flag, slug, supported countries, `fromPrice`) |
| `CountryCode` | Phone number country code (dial code, pattern, emoji flag) |
| `SupportedCountry` | Minimal country reference (name + code) within a region/package |
| `Destination` | Query parameters for fetching packages (code, name, slug, region) |
| `PackagePlan` | eSIM data plan (name, price, data GB, validity, country, networks, discounts) |
| `PackagesPage` | A page of packages plus its total count and any active promo code |
| `PackageDetail` | Detailed package info with activation status and expiry |
| `CheckStockResponse` | Stock availability check result |
| `EsimInfo` | Basic eSIM metadata (ICCID, matching ID, SM-DP+ address) |
| `AssignedEsim` | Full eSIM assigned to a customer (ICCID, packages, balance, settings) |
| `EsimProfile` | eSIM profile state from the SM-DP+ platform |
| `EsimProfileState` | Enum: ENABLED, DOWNLOADED, INSTALLED, DISABLED, DELETED, RELEASED, ERROR |
| `OrderDetail` | Full order with pricing, eSIM profile, QR code, payment info, loyalty points |
| `OrdersPage` | A page of order history plus its total count and a `hasMore` flag |
| `OrderHistoryItem` | Summary order for history lists |
| `PurchaseCountry` | Country the purchase was made from (on `OrderHistoryItem`) |
| `OrderInfo` | Order info with customer details and QR code |
| `PaymentRequest` | Payment intent creation payload (package, customer, payment method, loyalty points) |
| `PaymentMethod` | Enum: STRIPE_INTENT, STRIPE_CHECKOUT, AGENT_PAYMENT, COMPLIMENTARY, VOUCHER, SPLIT_PAYMENT, PAY_WITH_POINTS, UNKNOWN (with `displayName` and `shouldShowAmount`) |
| `CurrencyObject` | Currency with symbol and ISO code |
| `QrCode` | QR code image (base64 + URL) |
| `NotificationSettings` | Notification preference (type + enabled flag) |
| `LoyaltyPointsDetail` | Loyalty points earned/spent on an order |
| `KredsQuoteRequest` | Request for Kreds-to-discount quote |
| `KredsQuoteResponse` | Quote result with pricing and points breakdown |
| `LoyaltyProvider` | Loyalty provider constants (`kreds`, `mokafaa`) |
| `MokafaaOtpInitiateRequest` | Mokafaa OTP initiation payload (purpose + platform) |
| `MokafaaOtpInitiateResponse` | OTP session info (session ID, expiry, masked phone number) |
| `MokafaaOtpValidateRequest` | OTP validation payload (session ID, OTP, points, package type) |
| `MokafaaOtpValidateResponse` | OTP validation result (status, points redeemed) |
| `MokafaaEnrollment` | Mokafaa enrollment state on the customer profile |
| `MokafaaElection` | Mokafaa election flag returned on registration |
| `VisaRewardsIframeResponse` | Visa rewards verification iframe URL and token |
| `VisaRewardsResponse` | Visa rewards eligibility, status, and reward details |
| `VoucherRedeemRequest` | Voucher code redemption request |
| `VoucherRedeemResponse` | Voucher redemption result (success flag + redirect URL) |
| `VerifyEmailRequest` | Email verification payload (email + token) |
| `DeleteProfileResponse` | Account deletion result |
| `GetTokenResponse` | OAuth token response (access token, refresh token, expiry) |
| `ProfileResponse` | Registration/profile-update response (customer fields) |
| `VerifyEmailResponse` | Email verification result |
| `ChangePasswordResponse` | Password change/reset result |
| `PaymentResponse` | Payment intent response (URI, order ID, ephemeral key, publishable key) |
| `CheckoutCouponResponse` | Promo code application result (valid flag, discount, percentage) |
| `KredsLoyaltyBalanceResponse` | Loyalty balance (total points + detail) |
| `UserLocationResponse` | User's detected location (country, city, coordinates) |
| `RestrictedCountry` | Country with purchase restrictions |
| `RatingApiResponse` | Store review summary (verdict, counts, average rating, reviews, stats) |
| `Review` | A single store review (rating, title, comments, author, date) |
| `Author` | Review author (name, location) |
| `Stats` | Review statistics (company totals and per-star ratings) |
| `CompanyStats` | Company-level review count and average rating |
| `Ratings` | Per-star review counts |
| `Faq` | A single destination FAQ (question + answer) |
| `DestinationFaqResponse` | FAQ list for a destination (slug, name, language, faqs) |
| `ThemePage` | Theme for a page (url path, featured image, accent colour) |
| `ThemeDestination` | Theme for a destination (image, gallery, country code) |
| `ThemeImage` | Theme image (url + accent colour) |
| `ApiErrorResponse` | Standardized API error (detail, error, message) |
| `IframeRequest` | Iframe vendor request |
| `BaseResponse<T>` | Paginated response wrapper (count, next, previous, results) |
| `RepositoryResult<T>` | Read result: `value`, `isStale`, `failure` (in `io.esimplified.sdk.repository`) |
| `SdkError` | Sealed error type returned in `RepositoryResult.failure` (in `io.esimplified.sdk.network`) |
| `ProfileReusePolicy` | eSIM profile reuse policy |

## Caching and offline reads

Every list and detail read is served through an in-process cache keyed by call and arguments. Two optional arguments appear on those methods:

- **`forceRefresh: Boolean = false`** — skip the cache and go to the network. (`LoyaltyRepository.getLoyaltyBalance` defaults to `true`; a points balance is never served from cache unless you ask for it.)
- **`cacheTTL: Duration`** — how long this read stays fresh. Each repository exposes its own default as a constant, e.g. `EsimRepository.ESIM_LIST_TTL`.

Each cached method has a `…Result` twin returning `RepositoryResult<T>` instead of throwing:

```kotlin
data class RepositoryResult<T>(
    val value: T,
    val isStale: Boolean = false,   // value came from an expired cache entry
    val failure: SdkError? = null,  // what the refresh failed with, if it did
) {
    val didFail: Boolean
    val isOffline: Boolean          // failure is SdkError.NoInternetConnection
}
```

That lets a screen show last-known data with an "offline" banner rather than an error page:

```kotlin
val result = esimRepo.getEsimsResult()
render(result.value)
if (result.isStale && result.isOffline) showOfflineBanner()
```

The plain (non-`Result`) methods keep the old behaviour: a cache miss plus a failed refresh throws. When a stale entry exists they return it rather than throwing.

`SdkError` (in `io.esimplified.sdk.network`) is a sealed subclass of `IOException`:

| Case | Meaning |
|---|---|
| `NetworkError(statusCode, message)` | Server responded with an error status |
| `AuthenticationRequired` | No valid session |
| `NoInternetConnection` | Host unreachable / connection refused |
| `DecodingError(cause)` | Response did not match the model |
| `InvalidURL(url)` | Malformed URL |
| `Unknown(cause)` | Anything else |

Cache control:

- `EsimplifiedSdk.clearAllCaches()` — drop everything. `logout()` already does this for you; call it directly only when you want a clean slate without ending the session.
- `repository.invalidateCache()` — drop just that repository's entries.

## All Repository Methods

All repositories are provided as Koin singletons. Inject them by interface type.

Where a signature below shows `forceRefresh` / `cacheTTL`, see [Caching and offline reads](#caching-and-offline-reads). Every such method also has a `…Result` twin returning `RepositoryResult<…>`; they are listed once per repository rather than repeated per method.

### AuthRepository

Authentication, registration, password management, profile operations, and session control.

| Method | Signature | Description |
|---|---|---|
| `login` | `suspend fun login(email: String, password: String): Customer` | Authenticate with email/password, persist session |
| `loginWithRefreshToken` | `suspend fun loginWithRefreshToken(refreshToken: String): Customer` | Re-authenticate using a stored refresh token |
| `signInWithGoogle` | `suspend fun signInWithGoogle(email, firstName, lastName, fullName, phoneNumber, providerAccountId, idToken): Customer` | Authenticate via Google Sign-In |
| `register` | `suspend fun register(email, password, firstName, lastName, phoneNumber, marketingConsent, referredBy?, loyaltyElection?): ProfileResponse` | Create a new customer account (optionally electing a loyalty provider) |
| `forgotPassword` | `suspend fun forgotPassword(email: String): CustomerForgetPasswordResponse` | Request a password reset email |
| `changePassword` | `suspend fun changePassword(currentPassword: String, newPassword: String): ChangePasswordResponse` | Change password for authenticated user |
| `resetPassword` | `suspend fun resetPassword(email: String, token: String, newPassword: String): ChangePasswordResponse` | Reset password using email token |
| `verifyEmail` | `suspend fun verifyEmail(email: String, token: String, orderUUID: String?): VerifyEmailResponse` | Verify email address with token |
| `deleteProfile` | `suspend fun deleteProfile(): DeleteProfileResponse` | Delete the authenticated user's account |
| `fetchProfile` | `suspend fun fetchProfile(): Customer?` | `GET api/v2/customer/` — the customer of record. Returns `null` when unauthenticated, otherwise fetches the full profile, merges in the loyalty provider and enrolment from `customer/preferences/`, and saves it to the session |
| `getUser` | `suspend fun getUser(): Customer?` | Alias for `fetchProfile()` |
| `updatePreferences` | `suspend fun updatePreferences(preferredLanguage: String?, preferredCurrency: String?): Customer` | Update language/currency preferences, then re-fetch the full customer |
| `updateProfile` | `suspend fun updateProfile(email, firstName?, lastName?, phoneNumber?, password): ProfileResponse` | Update profile fields (requires password confirmation) |
| `updateCustomerProfile` | `suspend fun updateCustomerProfile(firstName?, lastName?, phoneNumber?, email?, password?): ProfileResponse` | Partial profile update — every field optional — then re-fetch the full customer |
| `logout` | `suspend fun logout()` | Clear stored session, tokens and every cached read |

**On `fetchProfile` and the re-fetch:** `updatePreferences` and `updateCustomerProfile` call `fetchProfile()` after a successful write, so the `Customer` you hold afterwards is the server's, not a locally patched copy. If that re-fetch fails the SDK falls back to the 1.x behaviour — patching the cached customer with the fields you just sent — so an update never fails because the follow-up read did.

### CountryRepository

Destination country browsing and search.

| Method | Signature | Description |
|---|---|---|
| `getCountries` | `suspend fun getCountries(forceRefresh: Boolean = false, cacheTTL: Duration = COUNTRIES_TTL): List<Country>` | Fetch all supported destination countries |
| `getCountriesBy` | `suspend fun getCountriesBy(destination: Destination, forceRefresh: Boolean = false, cacheTTL: Duration = COUNTRIES_TTL): List<Country>` | Filter countries by code, name, or region |
| `search` | `suspend fun search(query: String): List<Country>` | Search countries by name (never cached) |
| `getUserLocation` | `suspend fun getUserLocation(): UserLocationResponse` | Detect user's current country via IP (never cached) |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twins: `getCountriesResult`, `getCountriesByResult`. Default TTL: `COUNTRIES_TTL` = 24 h.

### PackagesRepository

eSIM data package browsing and stock checks.

| Method | Signature | Description |
|---|---|---|
| `getPackages` | `suspend fun getPackages(destination: Destination, forceRefresh: Boolean = false, cacheTTL: Duration = PACKAGES_TTL): List<PackagePlan>` | Fetch packages for a destination |
| `getPackagesPage` | `suspend fun getPackagesPage(destination: Destination, forceRefresh: Boolean = false, cacheTTL: Duration = PACKAGES_TTL): PackagesPage` | Same read, but also returns the total count and any promo code the API attached to the page |
| `getTopUpPackages` | `suspend fun getTopUpPackages(iccid: String, forceRefresh: Boolean = false, cacheTTL: Duration = PACKAGES_TTL): List<PackagePlan>` | Fetch top-up packages for an existing eSIM |
| `checkStock` | `suspend fun checkStock(packageTypeId: Int, forceRefresh: Boolean = false, cacheTTL: Duration = PACKAGES_TTL): CheckStockResponse` | Check if a specific package is in stock |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twins: `getPackagesResult`, `getPackagesPageResult`, `getTopUpPackagesResult`, `checkStockResult`. Default TTL: `PACKAGES_TTL` = 1 h.

> `getPackageRating()` moved to [`StoreReviewRepository.fetchStoreReview()`](#storereviewrepository) in 2.0.

### EsimRepository

eSIM lifecycle management for authenticated users.

| Method | Signature | Description |
|---|---|---|
| `getEsims` | `suspend fun getEsims(showLegacy: Boolean = true, isPrimary: Boolean? = null, forceRefresh: Boolean = false, cacheTTL: Duration = ESIM_LIST_TTL, includeBase64QrCode: Boolean = false): List<AssignedEsim>` | Fetch all eSIMs assigned to the customer |
| `getActiveEsims` | same parameters as `getEsims` | Fetch only non-archived eSIMs |
| `getArchivedEsims` | same parameters as `getEsims`, except `showLegacy: Boolean? = null` | Fetch only archived eSIMs. `showLegacy = null` leaves `show_legacy` out of the request entirely |
| `getEsimByIccid` | `suspend fun getEsimByIccid(iccid: String, forceRefresh: Boolean = false, cacheTTL: Duration = ESIM_DETAILS_TTL, includeBase64QrCode: Boolean = false): AssignedEsim` | Fetch a single eSIM from `customer/esims/{iccid}/details/` |
| `updateEsim` | `suspend fun updateEsim(iccid: String, name: String? = null, isAutoTopUp: Boolean? = null, isArchived: Boolean? = null, isPrimary: Boolean? = null)` | Update eSIM settings. Throws if the server rejects the write |
| `updateEsimPrimaryStatus` | `suspend fun updateEsimPrimaryStatus(iccid: String, isPrimary: Boolean)` | Convenience wrapper for the primary flag |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop both the list and detail cache entries |

`…Result` twins: `getEsimsResult`, `getActiveEsimsResult`, `getArchivedEsimsResult`, `getEsimByIccidResult`. Default TTLs: `ESIM_LIST_TTL` = 24 h, `ESIM_DETAILS_TTL` = 5 min.

**`includeBase64QrCode`** asks the API to embed the QR code image with the eSIM, so an install screen needs one request rather than two. When it is set, `AssignedEsim.qrCodeImageBase64` is populated alongside `smDpAddress` and `activationCode`, and `AssignedEsim.canInstallDirectly` reports whether the eSIM carries enough to hand straight to the Android eSIM installer — no order lookup required:

```kotlin
val esim = esimRepo.getEsimByIccid(iccid, includeBase64QrCode = true)
if (esim.canInstallDirectly) {
    val activation = listOf("LPA:1", esim.smDpAddress, esim.activationCode).joinToString("$")
    install(activation)
}
```

Lists are requested newest-first (`order_by=-assigned_date`) and capped at 1000 entries, matching the iOS SDK.

### OrdersRepository

Order history, order details, invoices, and conversion tracking.

| Method | Signature | Description |
|---|---|---|
| `getOrderHistory` | `suspend fun getOrderHistory(forceRefresh: Boolean = false, cacheTTL: Duration = ORDERS_LIST_TTL): List<OrderHistoryItem>` | Fetch past orders |
| `getOrderHistory` | `suspend fun getOrderHistory(withLoyaltyPoints: Boolean, forceRefresh: Boolean = false, cacheTTL: Duration = ORDERS_LIST_TTL): List<OrderHistoryItem>` | Fetch orders with loyalty points data |
| `getOrderDetails` | `suspend fun getOrderDetails(orderUuid: String, forceRefresh: Boolean = false, cacheTTL: Duration = ORDER_DETAIL_TTL): OrderDetail` | Fetch full order details including eSIM profile and QR code |
| `getOrdersPageResult` | `suspend fun getOrdersPageResult(limit: Int = ORDERS_PAGE_LIMIT, offset: Int = 0, withLoyaltyPoints: Boolean = false, forceRefresh: Boolean = false, cacheTTL: Duration = ORDERS_LIST_TTL): RepositoryResult<OrdersPage>` | Paged order read |
| `getOrderInvoice` | `suspend fun getOrderInvoice(orderUuid: String): ByteArray` | Download the order's PDF invoice bytes |
| `trackOrder` | `suspend fun trackOrder(orderUuid: String)` | Mark an order's conversion as tracked (never throws) |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twins: `getOrderHistoryResult` (both overloads), `getOrderDetailsResult`. Default TTLs: `ORDERS_LIST_TTL` = 10 min, `ORDER_DETAIL_TTL` = 5 min.

**Pending orders:** `getOrderDetails` retries an order whose `orderStatus` is still `pending` up to 5 times, one second apart, before returning it. A checkout screen can call it straight after Stripe confirms without polling itself.

### PaymentsRepository

Payment intent creation for Stripe/Google Pay checkout.

| Method | Signature | Description |
|---|---|---|
| `getPaymentIntent` | `suspend fun getPaymentIntent(request: PaymentRequest): PaymentResponse` | Create a payment intent for checkout |

### PromoCodeRepository

Promotional code management.

| Method | Signature | Description |
|---|---|---|
| `addPromoCode` | `suspend fun addPromoCode(code: String): CheckoutCouponResponse` | Apply a promo code to the customer's account |
| `getPromoCode` | `suspend fun getPromoCode(): CheckoutCouponResponse` | Retrieve the currently applied promo code |
| `removePromoCode` | `suspend fun removePromoCode(): CheckoutCouponResponse` | Remove the applied promo code |

### LoyaltyRepository

Loyalty program balance, quotes, and Mokafaa OTP flows (enrollment and checkout burn).

| Method | Signature | Description |
|---|---|---|
| `getLoyaltyBalance` | `suspend fun getLoyaltyBalance(forceRefresh: Boolean = true, cacheTTL: Duration = KREDS_BALANCE_TTL): KredsLoyaltyBalanceResponse` | Fetch the customer's current loyalty balance. Note `forceRefresh` defaults to `true` |
| `getKredsQuote` | `suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse` | Get a discount quote for applying Kreds to a package |
| `getMokafaaQuote` | `suspend fun getMokafaaQuote(packageTypeId: Int, loyaltyPointsToUse: Int): KredsQuoteResponse` | Get a discount quote for applying Mokafaa points to a package |
| `initiateMokafaaOtp` | `suspend fun initiateMokafaaOtp(purpose: String, platform: String = "android"): MokafaaOtpInitiateResponse` | Start a Mokafaa OTP session (`purpose`: `enrollment` or `checkout`) |
| `validateMokafaaOtp` | `suspend fun validateMokafaaOtp(sessionId: String, otp: String, points: Int? = null, packageTypeId: Int? = null): MokafaaOtpValidateResponse` | Validate the SMS OTP; `points` is required for checkout, omitted for enrollment |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twin: `getLoyaltyBalanceResult`. Default TTL: `KREDS_BALANCE_TTL` = 1 h.

Mokafaa methods throw `LoyaltyApiException(httpCode, message)` on HTTP errors — `message` is the backend error verbatim, `httpCode` lets callers branch on 400/401/503.

### ThemeRepository

Brand theming served by the API, so imagery and accent colours change without an app release.

| Method | Signature | Description |
|---|---|---|
| `fetchPageTheme` | `suspend fun fetchPageTheme(page: String, forceRefresh: Boolean = false, cacheTTL: Duration = THEME_TTL): ThemePage?` | Theme for a named page. `null` when the API has no theme for it |
| `fetchDestinationTheme` | `suspend fun fetchDestinationTheme(countryCode: String, forceRefresh: Boolean = false, cacheTTL: Duration = THEME_TTL): ThemeDestination?` | Theme for a destination, matched case-insensitively on country code |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twins: `fetchPageThemeResult`, `fetchDestinationThemeResult`. Default TTL: `THEME_TTL` = 1 h.

### FaqAndSupportRepository

Destination FAQ content.

| Method | Signature | Description |
|---|---|---|
| `fetchDestinationFaqs` | `suspend fun fetchDestinationFaqs(countryNameSlug: String, forceRefresh: Boolean = false, cacheTTL: Duration = FAQS_TTL): List<Faq>` | FAQs for a destination slug |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twin: `fetchDestinationFaqsResult`. Default TTL: `FAQS_TTL` = 24 h.

### StoreReviewRepository

App store review summary, used to drive rating prompts and social proof.

| Method | Signature | Description |
|---|---|---|
| `fetchStoreReview` | `suspend fun fetchStoreReview(forceRefresh: Boolean = false, cacheTTL: Duration = STORE_REVIEW_TTL): RatingApiResponse` | Store review summary, reviews, and stats |
| `invalidateCache` | `suspend fun invalidateCache()` | Drop this repository's cache entries |

`…Result` twin: `fetchStoreReviewResult`. Default TTL: `STORE_REVIEW_TTL` = 24 h.

### UserRepository

Local user preference flags (stored on device).

| Method | Signature | Description |
|---|---|---|
| `saveEsimSupportedPopupFlag` | `fun saveEsimSupportedPopupFlag(value: Boolean)` | Save whether the eSIM supported popup has been shown |
| `getEsimSupportedPopupFlag` | `fun getEsimSupportedPopupFlag(): Boolean` | Check if the eSIM supported popup was shown |
| `saveEsimNotSupporterPopupFlag` | `fun saveEsimNotSupporterPopupFlag(value: Boolean)` | Save whether the eSIM not-supported popup has been shown |
| `getEsimNotSupportedPopupFlag` | `fun getEsimNotSupportedPopupFlag(): Boolean` | Check if the eSIM not-supported popup was shown |

### NotificationRepository

Push notification settings management.

| Method | Signature | Description |
|---|---|---|
| `getSettings` | `suspend fun getSettings(): List<NotificationSettings>` | Fetch notification preferences |
| `updateSettings` | `suspend fun updateSettings(settings: List<NotificationSettings>)` | Update notification preferences |

### VisaRewardsRepository

Visa rewards verification and activation flow.

| Method | Signature | Description |
|---|---|---|
| `getIframe` | `suspend fun getIframe(isEU: Boolean): VisaRewardsIframeResponse` | Get the Visa verification iframe URL |
| `verify` | `suspend fun verify(token: String): VisaRewardsResponse` | Verify a Visa reward token |
| `activate` | `suspend fun activate(token: String, rewardCode: String): VisaRewardsResponse` | Activate a verified Visa reward |

### VouchersRepository

Voucher code redemption.

| Method | Signature | Description |
|---|---|---|
| `redeemVoucher` | `suspend fun redeemVoucher(code: String): Result<VoucherRedeemResponse>` | Redeem a voucher code |

## Errors and exceptions

Reads taken through a `…Result` method report failure in `RepositoryResult.failure` as an [`SdkError`](#caching-and-offline-reads) and do not throw. Everything else throws. These are the SDK's own public exception types:

| Exception | Thrown by | Meaning |
|---|---|---|
| `SdkError` (sealed, extends `IOException`) | cached reads, `getOrderInvoice`, the auth interceptor | Transport or decoding failure. See the case table under [Caching and offline reads](#caching-and-offline-reads) |
| `InvalidRefreshTokenException` | `AuthRepository.loginWithRefreshToken` | The stored refresh token was rejected. The session has already been cleared — send the user to sign-in |
| `LoyaltyApiException(httpCode, message)` | `LoyaltyRepository` Mokafaa methods | Backend error, `message` verbatim. Branch on `httpCode` (400 / 401 / 503) |
| `PaymentApiException(httpCode, type, message)` | `PaymentsRepository.getPaymentIntent` | Payment rejected. `type` is `"VALIDATION_ERROR"` for a bad request |
| `SecureStorageInitException` | `EsimplifiedSdk.initialize()` | `EncryptedSharedPreferences` could not be initialised. The SDK refuses to fall back to plaintext — sign the user out and re-prompt |

Other API failures surface as a plain `Exception` whose message is the backend's error text, parsed out of the response body.

## Authentication Flow

The SDK handles the complete authentication lifecycle internally.

### Login

```
App calls AuthRepository.login(email, password)
  -> SDK sends POST /auth/token/ (grant_type=password)
  -> API returns access_token, refresh_token, expires_in
  -> SDK saves tokens + user profile to EncryptedSharedPreferences
  -> SDK updates SessionManager state to Auth.Authenticated
  -> Returns Customer to the app
```

### Token Storage

Tokens and user data are persisted in `EncryptedSharedPreferences` (AES-256-GCM encryption, AES-256-SIV key encryption), handled by the internal `DefaultSecureStorage`.

If encrypted storage cannot be initialised, the SDK **throws `SecureStorageInitException` and does not fall back to plaintext `SharedPreferences`** — auth tokens would otherwise be written unencrypted to the device. Handle it where you call `EsimplifiedSdk.initialize()`: sign the user out and prompt them to authenticate again.

### Automatic Token Refresh

The `SdkAuthInterceptor` (an OkHttp interceptor) handles token refresh transparently:

1. Every authenticated API request includes a `Bearer` token
2. If the API returns `401 Unauthorized`, the interceptor automatically:
   - Sends a refresh token request to `POST /auth/token/` (grant_type=refresh_token)
   - Updates the stored tokens via `SessionManager.save()`
   - Retries the original request with the new access token
3. If the refresh **is rejected** — HTTP 400, 401 or 403, or there is no refresh token to send — the session ends and the user is signed out
4. If the refresh fails for any other reason — a 500, a gateway error, a dropped connection — the session is **kept** and the failure is surfaced to the caller as an `SdkError.NetworkError`

Point 4 is a behaviour change in 2.0. Before, any non-2xx refresh response ended the session, so a brief server-side blip signed users out.

The interceptor also adds:
- `Authorization: Basic {base64(clientId:clientSecret)}` for unauthenticated requests
- `x-auth-validation` header (AWS WAF token)
- `accept-currency` and `accept-language` headers from user preferences

### Auth State

```kotlin
sealed interface Auth {
    data object Unauthenticated : Auth
    data class Authenticated(
        var user: Customer,
        val expires: LocalDateTime,
        val accessToken: String,
        val refreshToken: String
    ) : Auth
}
```

Check auth state via:
- `SessionManager.isAuthenticated(): Boolean`
- `SessionManager.getAuthState(): Auth`
- `SessionManager.getAccessToken(): String`
- `SessionManager.getRefreshToken(): String`

### Logout

```kotlin
authRepo.logout()
```

Clears all stored tokens and user data from `EncryptedSharedPreferences`, resets `SessionManager` state to `Auth.Unauthenticated`, and drops every cached read so the next user on the device cannot be served the previous user's eSIMs or orders.

You no longer need to call `EsimplifiedSdk.clearAllCaches()` alongside it; leaving an existing call in place is harmless.

## Custom Storage

By default the SDK uses `EncryptedSharedPreferences` (AES-256) to persist tokens and session data. To use your own storage (e.g., a database or keystore wrapper), implement `SecureStorageProvider` and pass it during initialization:

```kotlin
class MyStorage : SecureStorageProvider {
    override fun secureLoad(key: String, default: String): String { /* ... */ }
    override fun secureSave(value: String, forKey: String) { /* ... */ }
    override fun clearSecureStorage() { /* ... */ }
    override fun <T> load(key: String, default: T): T { /* ... */ }
    override fun <T> save(value: T, forKey: String) { /* ... */ }
}

EsimplifiedSdk.initialize(
    context = this,
    config = config,
    storageProvider = MyStorage()
)
```

## Custom Headers

You can inject additional headers into every SDK request by providing a `customHeadersProvider`:

```kotlin
EsimplifiedSdk.initialize(
    context = this,
    config = SdkConfig(
        // ...
        customHeadersProvider = {
            mapOf(
                "X-Firebase-AppCheck" to getAppCheckToken(),
                "accept-currency" to selectedCurrency,
                "accept-language" to selectedLanguage,
            )
        }
    )
)
```

## Migrating from 1.x to 2.0

2.0 is a major release because model field types changed. The compiler catches most of it. **One category it does not catch is described under [The silent trap](#2-the-silent-trap-analytics-and-anything-typed-any) — read that section even if your build is green.**

### 1. Money fields are now `String`

Every money amount now carries the server's decimal text verbatim instead of a parsed `Double`. Display sites use the `String` as-is; arithmetic uses the new `…Value` accessor.

| Model | Field | 1.x | 2.0 | Fix |
|---|---|---|---|---|
| `PackagePlan` | `price` | `Double` | `String` | display: use as-is · arithmetic: `price` → `priceValue` |
| `PackagePlan` | `discountedPrice` | `Double?` | `String?` | display: use as-is · arithmetic: `discountedPrice` → `discountedPriceValue` |
| `PackagePlan` | `purchasePrice` | `Double` | `String` | display: use as-is · arithmetic: `purchasePrice` → `purchasePriceValue` |
| `OrderDetail` | `price` | `Double` | `String` | display: use as-is · arithmetic: `price` → `priceValue` |
| `OrderDetail` | `finalPrice` | `Double` | `String` | display: use as-is · arithmetic: `finalPrice` → `finalPriceValue` |
| `OrderDetail` | `discountAmount` | `Double` | `String` | display: use as-is · arithmetic: `discountAmount` → `discountAmountValue` |
| `Country` | `fromPrice` | `Double?` | `String?` | display: use as-is · arithmetic: `fromPrice` → `fromPriceValue` |

```kotlin
// 1.x
Text("$" + String.format("%.2f", plan.price))
val total = plan.purchasePrice * quantity

// 2.0
Text("$" + plan.price)                          // already formatted by the server
val total = plan.purchasePriceValue * quantity  // Double, for arithmetic
```

`PackagePlan.convertedPrice` is still `Double?` — it is `Double?` in the iOS SDK too, and did not change.

`PackagePlan.purchasePrice`, `isFreePurchase` and `hasDiscount` are now computed properties (`get()`) rather than values fixed at construction. Behaviour is the same; only `copy()` on a hand-built instance now recomputes them.

### 2. The silent trap: analytics, and anything typed `Any`

A `Map<String, Any>` accepts a `String` exactly as happily as it accepted a `Double`. **Nothing fails to compile, nothing throws at runtime, and the event ships with the wrong type.** Analytics payloads, `Bundle.putString`/`putDouble` pairs, JSON built by hand, and any `Any`-typed builder are all affected.

```kotlin
// Compiles before AND after. Before: numeric 9.99. After: the string "9.99".
analytics.logEvent("purchase", mapOf("value" to order.finalPrice))

// 2.0 — be explicit about the numeric type
analytics.logEvent("purchase", mapOf("value" to order.finalPriceValue))
```

Grep your app for every use of the seven fields in the table above and check each one by hand — not just the ones the compiler flagged:

```bash
grep -rn "\.price\b\|\.finalPrice\b\|\.discountedPrice\b\|\.purchasePrice\b\|\.discountAmount\b\|\.fromPrice\b" app/src
```

This is the one that nearly shipped wrong. Revenue reporting silently changing type is not something a green build will tell you about.

### 3. `Customer` notification flags (additive — nothing to fix)

`Customer` now carries the eight notification flags the live API actually sends. This is purely additive; no existing `Customer` field was removed or retyped.

| Field | JSON key |
|---|---|
| `receiveMarketingEmail` | `receive_marketing_email` |
| `receiveMarketingPush` | `receive_marketing_push` |
| `receiveAccountEmail` | `receive_account_email` |
| `receiveAccountSms` | `receive_account_sms` |
| `receiveAccountPush` | `receive_account_push` |
| `receivePurchaseEmail` | `receive_purchase_email` |
| `receivePurchasePush` | `receive_purchase_push` |
| `receiveViberMessages` | `receive_viber_messages` |

All eight are `Boolean?` defaulting to `null`, so `null` means "the API did not say", which is distinct from `false`. Treat `null` as unknown, not as off:

```kotlin
val marketingOn = customer.receiveMarketingEmail ?: false
```

Also added: `acquisitionSource: String?`, and `referralCode` now also decodes from the API's `unique_referral_code` key as well as `referral_code`.

### 4. `getPackageRating()` moved

`PackagesRepository.getPackageRating()` is gone. Use `StoreReviewRepository.fetchStoreReview()`, injected the same way:

```kotlin
// 1.x
val rating = packagesRepo.getPackageRating()

// 2.0
val rating = storeReviewRepo.fetchStoreReview()
```

`RatingApiResponse` gained `reviews: List<Review>?` and `stats: Stats?`, and its previously-required fields now have defaults.

### 5. Fields that became nullable

Existing call sites need a null check.

| Model | Field | 1.x | 2.0 | Why |
|---|---|---|---|---|
| `AssignedEsim` | `profile` | `EsimProfile` | `EsimProfile?` | An eSIM that has not been provisioned yet has no profile; the whole response used to fail to decode |
| `OrderHistoryItem` | `country` | `Country` | `Country?` | Same — one order without a country used to fail the whole history |
| `CheckStockResponse` | `packageInfo` | `PackagePlan` | `PackagePlan?` | An out-of-stock response may omit the package |
| `VisaRewardsResponse` | `used`, `allowed`, `remaining` | `Int` (default 0) | `Int?` (default `null`) | Distinguishes "the API sent zero" from "the API sent nothing" |

For `VisaRewardsResponse`, use the new `remainingOrAllowed: Int?` (`remaining ?: allowed`) where you previously read `remaining`.

### 6. New optional parameters on existing methods

All have defaults, so existing calls still compile. They are worth adopting:

- `getEsims` / `getActiveEsims` / `getArchivedEsims`: `showLegacy`, `isPrimary`, `forceRefresh`, `cacheTTL`, `includeBase64QrCode` (on the archived reads `showLegacy` is `Boolean?` — see section 9)
- `getEsimByIccid`: `forceRefresh`, `cacheTTL`, `includeBase64QrCode`
- `updateEsim`: `isPrimary`
- `getPackages` / `getTopUpPackages` / `checkStock` / `getCountries` / `getCountriesBy` / `getOrderHistory` / `getOrderDetails` / `getLoyaltyBalance`: `forceRefresh`, `cacheTTL`
- `SdkConfig`: `enableCaching`, `defaultCacheTtlSeconds`

### 7. Behaviour changes with no signature change

- **`updateEsim` now throws** when the server rejects the write. 1.x called an endpoint typed `Response<…>` and never checked `isSuccessful`, so a rejected rename or archive returned normally and looked like a success. 2.0 throws on a non-2xx response, and also when the success body is not the API's `eSIM updated successfully`. Wrap existing calls in the error handling you already use for other writes.
- **A failed token refresh no longer always ends the session.** Only 400/401/403 (or a missing refresh token) sign the user out; transient server and network failures keep the session and surface an error. If your app has a workaround that re-logs users in after a blip, you can remove it.
- **`updatePreferences()` and `updateCustomerProfile()` now return the server's customer**, re-fetched from `GET api/v2/customer/` after the write, rather than a locally patched copy. If the re-fetch fails, the 1.x local-copy behaviour is used as a fallback, so an update never fails because the follow-up read did. `getUser()` already read from `GET api/v2/customer/` in 1.x and is unchanged apart from keeping a referral code the profile response omits.
- **`getOrderDetails` retries a `pending` order** up to 5 times, one second apart. A checkout screen that polls on its own can stop.
- **Reads are cached in memory.** If your app relies on every call hitting the network, pass `forceRefresh = true` or set `enableCaching = false` in `SdkConfig`.
- **`logout()` now clears every cached read** as well as the session, so a second customer on the same device is never served the first one's eSIMs or orders. 1.x left the cache in place, which is why the 1.x advice was to call `EsimplifiedSdk.clearAllCaches()` on logout. That call is now redundant, and harmless if you keep it.

### 8. Seven unused request types were removed

These were declared but never used — no repository method took or returned any of them, and no endpoint referenced them. They are gone from `io.esimplified.sdk.model` in 2.0.

| Removed type | Was |
|---|---|
| `EsimRequest` | Request parameters for eSIM list queries |
| `EsimPackageListRequest` | Request for eSIM-specific package list |
| `OrderRequest` | Order query parameters |
| `SearchBody` | Search query payload |
| `CustomerSignIn` | Login request payload (email + password) |
| `AuthResponse` | Legacy session-auth response |
| `RewardActivationRequest` | Reward activation payload |

Nothing replaces them: the repository methods that cover these flows (`EsimRepository.getEsims`, `PackagesRepository.getTopUpPackages`, `OrdersRepository.getOrderDetails`, `CountryRepository.search`, `AuthRepository.login`, `VisaRewardsRepository.activate`) take plain arguments and always did. If your app constructs one of these types, delete the construction — the argument it was feeding is already a parameter on the method you call.

`RestrictedCountry`, `RestrictionType` and `RestrictedFor` are **not** removed. They are decoded by the app from remote config rather than by the SDK, which is why they look unused from inside the SDK.

### 9. `getArchivedEsims` no longer sends `show_legacy`

`getArchivedEsims` and `getArchivedEsimsResult` now take `showLegacy: Boolean? = null` instead of `showLegacy: Boolean = true`, and `null` means the SDK leaves `show_legacy` out of the request altogether. The archived read now sends `show_archived_esims=true` and no legacy flag, which is what the API expects for that list.

| | 1.x and 2.0-beta | 2.0 |
|---|---|---|
| `getArchivedEsims()` | `…&show_archived_esims=true&show_legacy=true` | `…&show_archived_esims=true` |
| `getArchivedEsims(showLegacy = true)` | `…&show_legacy=true` | `…&show_legacy=true` (unchanged) |
| `getActiveEsims()` | `…&show_legacy=true` | `…&show_legacy=true` (unchanged) |
| `getEsims()` | both legs send `show_legacy=true` | both legs send `show_legacy=true` (unchanged) |

Existing calls that pass `showLegacy = true` to an archived read still compile and still send the flag; they are now redundant and can be dropped. Calls that pass nothing get the new behaviour.

**If you implement `EsimRepository` yourself** — a test fake, for instance — update those two overrides to `showLegacy: Boolean?`, or they will no longer override the interface.

The archived list's cache key changed with it: `esims_true_legacyunset_…` when the flag is omitted, `esims_true_legacytrue_…` when it is passed. Only in-memory keys, nothing persisted.

### Checklist

- [ ] Build, and fix every money-field type error with the `…Value` accessor or the `String` verbatim
- [ ] Grep for the seven money fields and check every `Any`-typed / analytics use by hand
- [ ] Treat the eight new notification flags' `null` as unknown, not `false`
- [ ] Move `getPackageRating()` to `StoreReviewRepository.fetchStoreReview()`
- [ ] Null-check `AssignedEsim.profile`, `OrderHistoryItem.country`, `CheckStockResponse.packageInfo`
- [ ] Handle the exception `updateEsim` can now throw
- [ ] Delete any construction of the seven removed request types
- [ ] Drop the `EsimplifiedSdk.clearAllCaches()` call from your logout path — `logout()` does it
- [ ] Widen any `EsimRepository` implementation of `getArchivedEsims` / `getArchivedEsimsResult` to `showLegacy: Boolean?`

## Support

For credentials, integration help, or to report a bug, contact:

- **Email:** support@esimplified.io

---

# Building from Source

If you want to verify the SDK builds cleanly or inspect the source:

```bash
git clone https://github.com/eSimplified/esimplified-android-sdk.git
cd esimplified-android-sdk

# Compile the SDK
./gradlew :sdk:assembleRelease

# Run tests
./gradlew test
```

The compiled AAR lands at `sdk/build/outputs/aar/sdk-release.aar`.

## ProGuard

The SDK ships consumer ProGuard rules (`consumer-rules.pro`) that are automatically applied to consuming apps during their release builds. Consuming apps do not need to add any additional ProGuard rules for the SDK.

## Tech Stack

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 2.2.20 | Language |
| kotlinx.serialization | 1.9.0 | JSON serialization/deserialization |
| kotlinx.coroutines | 1.10.2 | Asynchronous operations |
| Retrofit | 2.11.0 | HTTP client |
| OkHttp | 4.12.0 | HTTP transport + interceptors |
| Koin | 4.1.1 | Dependency injection |
| Timber | 5.0.1 | Logging |
| AndroidX Security Crypto | 1.1.0-alpha06 | EncryptedSharedPreferences |
| Android Gradle Plugin | 8.13.2 | Build tooling |

## License

Proprietary — © 2026 eSimplified Ltd. See [LICENSE](LICENSE) for the full terms.

Use of the SDK requires API credentials issued by eSimplified and is governed by your commercial agreement.
