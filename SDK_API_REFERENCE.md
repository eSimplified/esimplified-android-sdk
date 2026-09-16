# eSimplified Android SDK — API Reference

Documents `io.github.esimplified:android-sdk:2.0.0`.

Upgrading from 1.x? Start with [Migrating from 1.x to 2.0](README.md#migrating-from-1x-to-20) in the README — money fields changed type, seven unused request types were removed (`EsimRequest`, `EsimPackageListRequest`, `OrderRequest`, `SearchBody`, `CustomerSignIn`, `AuthResponse`, `RewardActivationRequest`), and the compiler does not catch every call site.

## Initialization

### EsimplifiedSdk.initialize()

```kotlin
EsimplifiedSdk.initialize(
    context: Context,
    config: SdkConfig,
    storageProvider: SecureStorageProvider? = null,
    sessionManager: SessionManager? = null
)
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| context | Context | Yes | Application context |
| config | SdkConfig | Yes | SDK configuration |
| storageProvider | SecureStorageProvider | No | Custom token storage (defaults to EncryptedSharedPreferences) |
| sessionManager | SessionManager | No | Custom session handler (defaults to DefaultSessionManager) |

### SdkConfig

```kotlin
SdkConfig(
    environment: SdkEnvironment,
    clientName: String,
    apiVersion: String = "v2",
    clientId: String,
    clientSecret: String,
    awsWafToken: String = "",
    enableLogging: Boolean = false,
    customHeadersProvider: (() -> Map<String, String>)? = null,
    enableCaching: Boolean = true,
    defaultCacheTtlSeconds: Long = 3600,
    logger: SdkLogger? = null
)
```

### SdkEnvironment

| Value | Description |
|-------|-------------|
| `STAGING` | `https://{clientName}.stage.esimplified.io` |
| `TESTING` | `https://{clientName}.test.esimplified.io` (short token lifetimes, for auth testing) |
| `PRODUCTION` | `https://{clientName}.live.esimplified.io` |

### EsimplifiedSdk.koinModule()

Returns a Koin `Module` containing all SDK dependencies. Add to your Koin setup:

```kotlin
startKoin {
    modules(EsimplifiedSdk.koinModule(), yourAppModule)
}
```

**Koin is required in your app.** This function returns an
`org.koin.core.module.Module`, and `startKoin` is Koin's. The SDK publishes
`koin-core` at runtime scope only, so neither type is on your compile
classpath until you declare Koin yourself:

```kotlin
dependencies {
    implementation("io.github.esimplified:android-sdk:2.0.0")
    implementation("io.insert-koin:koin-android:4.1.1")
}
```

Use `koin-androidx-compose` instead if you inject into Compose. Keep Koin on
the same 4.x version the SDK uses to avoid a duplicate-class conflict.

### EsimplifiedSdk.clearAllCaches()

Drops every cached read. `AuthRepository.logout()` already does this, so call it directly only when you want a clean slate without ending the session.

### SdkLogger

`SdkConfig(logger = …)` takes a `fun interface SdkLogger { fun log(level: SdkLogLevel, message: String, throwable: Throwable?) }`, with `SdkLogLevel` of `DEBUG`, `WARNING` or `ERROR`. Supply one to route the SDK's own diagnostics into your logging; leave it null and the SDK writes to `android.util.Log` under the tag `EsimplifiedSdk` only when the app is debuggable or `enableLogging = true`. The SDK has no logging dependency, and no line carries an email, token, ICCID, customer id, order UUID, voucher code or raw body.

### EsimplifiedSdk.sessionManager

The active `SessionManager`, for reading auth state outside a repository.

---

## Reads, caching and errors

Cached reads take two optional arguments, omitted from the tables below for brevity:

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `forceRefresh: Boolean` | `false` (`true` on `getLoyaltyBalance` and `getLoyaltyBalanceResult`) | Skip the cache and go to the network |
| `cacheTTL: Duration` | the repository's own constant | How long this read stays fresh |

Each such method also has a `…Result` twin returning `RepositoryResult<T>` rather than throwing:

```kotlin
data class RepositoryResult<T>(
    val value: T,
    val isStale: Boolean = false,   // served from an expired cache entry
    val failure: SdkError? = null,  // why the refresh failed, if it did
) {
    val didFail: Boolean
    val isOffline: Boolean
}
```

`SdkError` is a sealed subclass of `IOException`: `NetworkError(statusCode, message)`, `AuthenticationRequired`, `NoInternetConnection`, `DecodingError(cause)`, `InvalidURL(url)`, `Unknown(cause)`.

Every cached repository also exposes `suspend fun invalidateCache()`.

---

## Repositories

All repository functions are `suspend` unless noted. Inject via Koin, which
your app must declare as a dependency — see
[`EsimplifiedSdk.koinModule()`](#esimplifiedsdkkoinmodule):

```kotlin
val authRepo: AuthRepository = koinInject()
```

---

### AuthRepository

#### Login & Registration

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `login` | `email: String, password: String` | `Customer` | Email/password login |
| `loginWithRefreshToken` | `refreshToken: String` | `Customer` | Login using stored refresh token |
| `signInWithGoogle` | `email: String, firstName: String, lastName: String, fullName: String, phoneNumber: String, providerAccountId: String, idToken: String` | `Customer` | Google OAuth sign-in |
| `register` | `email: String, password: String, firstName: String, lastName: String, phoneNumber: String, marketingConsent: Boolean?, referredBy: String? = null, loyaltyElection: String? = null` | `ProfileResponse` | Create new account |

#### Password Management

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `forgotPassword` | `email: String` | `CustomerForgetPasswordResponse` | Send password reset email |
| `changePassword` | `currentPassword: String, newPassword: String` | `ChangePasswordResponse` | Change password (authenticated) |
| `resetPassword` | `email: String, token: String, newPassword: String` | `ChangePasswordResponse` | Reset password with token |

#### Profile & Session

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `fetchProfile` | — | `Customer?` | `GET api/v2/customer/` — the customer of record. `null` when unauthenticated |
| `getUser` | — | `Customer?` | Alias for `fetchProfile()` |
| `updateProfile` | `email: String, firstName: String?, lastName: String?, phoneNumber: String?, password: String` | `ProfileResponse` | Update user profile, then re-fetch the customer |
| `updateCustomerProfile` | `firstName: String? = null, lastName: String? = null, phoneNumber: String? = null, email: String? = null, password: String? = null` | `ProfileResponse` | Partial profile update, then re-fetch the customer |
| `updatePreferences` | `preferredLanguage: String?, preferredCurrency: String?` | `Customer` | Update language/currency prefs, then re-fetch the customer |
| `verifyEmail` | `email: String, token: String, orderUUID: String?` | `VerifyEmailResponse` | Verify email address |
| `deleteProfile` | — | `DeleteProfileResponse` | Delete user account |
| `logout` | — | `Unit` | End the session and drop every cached read, so the next customer on the device is not served the previous one's data |

---

### CountryRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getCountries` | — | `List<Country>` | Get all available countries |
| `getCountriesBy` | `destination: Destination` | `List<Country>` | Filter by code, name, slug, or region |
| `search` | `query: String` | `List<Country>` | Search countries by name/code |
| `getUserLocation` | — | `UserLocationResponse` | Get user's location by IP (never cached) |

Cached: `getCountries`, `getCountriesBy` (`COUNTRIES_TTL` = 24 h). `…Result` twins: `getCountriesResult`, `getCountriesByResult`.

---

### PackagesRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getPackages` | `destination: Destination` | `List<PackagePlan>` | Get eSIM packages for a destination |
| `getPackagesPage` | `destination: Destination` | `PackagesPage` | Same read plus total count and the page's promo code |
| `getTopUpPackages` | `iccid: String` | `List<PackagePlan>` | Get top-up packages for an existing eSIM |
| `checkStock` | `packageTypeId: Int` | `CheckStockResponse` | Check package availability |

All cached (`PACKAGES_TTL` = 1 h). `…Result` twins: `getPackagesResult`, `getPackagesPageResult`, `getTopUpPackagesResult`, `checkStockResult`.

`getPackageRating()` moved to `StoreReviewRepository.fetchStoreReview()` in 2.0.

---

### EsimRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getEsims` | `showLegacy: Boolean = true, isPrimary: Boolean? = null, includeBase64QrCode: Boolean = false` | `List<AssignedEsim>` | Get all user's eSIMs |
| `getActiveEsims` | same as `getEsims` | `List<AssignedEsim>` | Non-archived eSIMs only |
| `getArchivedEsims` | same as `getEsims`, but `showLegacy: Boolean? = null` | `List<AssignedEsim>` | Archived eSIMs only. The default omits `show_legacy` from the request; pass `true`/`false` to send it |
| `getEsimByIccid` | `iccid: String, includeBase64QrCode: Boolean = false` | `AssignedEsim` | Get one eSIM from `customer/esims/{iccid}/details/` |
| `updateEsim` | `iccid: String, name: String? = null, isAutoTopUp: Boolean? = null, isArchived: Boolean? = null, isPrimary: Boolean? = null` | `Unit` | Update eSIM settings. **Throws if the server rejects the write** — on a non-2xx response, or when the success body is not the API's `eSIM updated successfully`, matching the iOS SDK |
| `updateEsimPrimaryStatus` | `iccid: String, isPrimary: Boolean` | `Unit` | Convenience wrapper for the primary flag |

Cached: `ESIM_LIST_TTL` = 24 h for lists, `ESIM_DETAILS_TTL` = 5 min for details. `…Result` twins: `getEsimsResult`, `getActiveEsimsResult`, `getArchivedEsimsResult`, `getEsimByIccidResult`.

`includeBase64QrCode = true` asks the API to embed the QR image, populating `AssignedEsim.qrCodeImageBase64` alongside `smDpAddress` and `activationCode`. `AssignedEsim.canInstallDirectly` reports whether those are enough to install without an order lookup. Lists are requested newest-first (`order_by=-assigned_date`), capped at 1000.

---

### OrdersRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getOrderHistory` | — | `List<OrderHistoryItem>` | Get past orders |
| `getOrderHistory` | `withLoyaltyPoints: Boolean` | `List<OrderHistoryItem>` | Get orders with loyalty points data |
| `getOrderDetails` | `orderUuid: String` | `OrderDetail` | Get detailed order info |
| `getOrdersPageResult` | `limit: Int = 100, offset: Int = 0, withLoyaltyPoints: Boolean = false` | `RepositoryResult<OrdersPage>` | Paged order read |
| `getOrderInvoice` | `orderUuid: String` | `ByteArray` | Download the order's PDF invoice bytes |
| `trackOrder` | `orderUuid: String` | `Unit` | Mark the order's conversion as tracked (never throws) |

Cached: `ORDERS_LIST_TTL` = 10 min, `ORDER_DETAIL_TTL` = 5 min. `…Result` twins: `getOrderHistoryResult` (both overloads), `getOrderDetailsResult`.

`getOrderDetails` retries an order still in `pending` status up to 5 times, one second apart, before returning it.

---

### PaymentsRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getPaymentIntent` | `request: PaymentRequest` | `PaymentResponse` | Create Stripe payment intent |

**PaymentRequest:**

```kotlin
PaymentRequest(
    type: String,              // "buy" or "top-up"
    iccid: String?,            // Required for top-up
    customer: CustomerDetails,
    packageTypeId: Int,
    paymentMethod: String,     // "stripe_intent" or "stripe_checkout"
    autoTopUp: Boolean,
    savePaymentMethod: Boolean,
    loyaltyPointsAmount: Double? = null
)
```

---

### PromoCodeRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `addPromoCode` | `code: String` | `CheckoutCouponResponse` | Apply promo code |
| `getPromoCode` | — | `CheckoutCouponResponse` | Get current promo code |
| `removePromoCode` | — | `CheckoutCouponResponse` | Remove promo code |

---

### LoyaltyRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getLoyaltyBalance` | `forceRefresh: Boolean = true` | `KredsLoyaltyBalanceResponse` | Get Kreds points balance. Note `forceRefresh` defaults to `true` |
| `getLoyaltyBalanceResult` | `forceRefresh: Boolean = true` | `RepositoryResult<KredsLoyaltyBalanceResponse?>` | The same read, reported rather than thrown. See the note below |
| `getKredsQuote` | `packageTypeId: Int, loyaltyPointsAmount: Double` | `KredsQuoteResponse` | Get pricing quote with Kreds |
| `getMokafaaQuote` | `packageTypeId: Int, loyaltyPointsToUse: Int` | `KredsQuoteResponse` | Get pricing quote with Mokafaa points |
| `initiateMokafaaOtp` | `purpose: String, platform: String = "android"` | `MokafaaOtpInitiateResponse` | Start a Mokafaa OTP session (`purpose`: `enrollment` or `checkout`); countdown should be driven by `expiresAt` |
| `validateMokafaaOtp` | `sessionId: String, otp: String, points: Int? = null, packageTypeId: Int? = null` | `MokafaaOtpValidateResponse` | Validate the SMS OTP; `points` required for checkout, omitted for enrollment |

Mokafaa methods throw `LoyaltyApiException(httpCode, message)` on HTTP errors — `message` is the backend error verbatim, `httpCode` lets callers branch on 400/401/503.

**`getLoyaltyBalance` vs `getLoyaltyBalanceResult`** — both perform the identical cached read; they differ only in how a failure reaches you.

`getLoyaltyBalanceResult` is the underlying call. It never throws for a network or backend failure: it returns a `RepositoryResult` whose `value` is the balance, and on failure falls back to the expired cache entry with `isStale = true` and `failure` set to the `SdkError`. If nothing was ever cached, `value` is `null` — which is why the result type is `KredsLoyaltyBalanceResponse?` rather than non-null.

`getLoyaltyBalance` calls it and unwraps. A non-null value is returned as-is; if `value` is `null` it rethrows the recorded failure, so it always hands back a non-null balance or throws.

Use `getLoyaltyBalanceResult` when you want to render a last-known balance while offline and flag it as stale; use `getLoyaltyBalance` when a balance you cannot obtain should be an error path.

```kotlin
val result = loyaltyRepo.getLoyaltyBalanceResult(forceRefresh = false)
val balance = result.value            // null only if nothing cached and the refresh failed
if (result.isStale) showStaleBadge()  // served from an expired entry
if (result.isOffline) showOfflineHint()
```

---

### ThemeRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `fetchPageTheme` | `page: String` | `ThemePage?` | Theme for a named page; `null` if the API has none |
| `fetchDestinationTheme` | `countryCode: String` | `ThemeDestination?` | Theme for a destination, matched case-insensitively |

Cached (`THEME_TTL` = 1 h). `…Result` twins: `fetchPageThemeResult`, `fetchDestinationThemeResult`.

---

### FaqAndSupportRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `fetchDestinationFaqs` | `countryNameSlug: String` | `List<Faq>` | FAQs for a destination slug |

Cached (`FAQS_TTL` = 24 h). `…Result` twin: `fetchDestinationFaqsResult`.

---

### StoreReviewRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `fetchStoreReview` | — | `RatingApiResponse` | Store review summary, reviews and stats |

Cached (`STORE_REVIEW_TTL` = 24 h). `…Result` twin: `fetchStoreReviewResult`.

---

### VisaRewardsRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getIframe` | `isEU: Boolean` | `VisaRewardsIframeResponse` | Get Visa rewards iframe URL |
| `verify` | `token: String` | `VisaRewardsResponse` | Verify a Visa rewards token |
| `activate` | `token: String, rewardCode: String` | `VisaRewardsResponse` | Activate a Visa reward |

---

### VouchersRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `redeemVoucher` | `code: String` | `Result<VoucherRedeemResponse>` | Redeem a voucher code |

---

### NotificationRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getSettings` | — | `List<NotificationSettings>` | Get notification preferences |
| `updateSettings` | `settings: List<NotificationSettings>` | `Unit` | Update notification preferences |

---

### UserRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `saveEsimSupportedPopupFlag` | `value: Boolean` | `Unit` | Save eSIM supported popup state |
| `getEsimSupportedPopupFlag` | — | `Boolean` | Get eSIM supported popup state |
| `saveEsimNotSupporterPopupFlag` | `value: Boolean` | `Unit` | Save eSIM not-supported popup state |
| `getEsimNotSupportedPopupFlag` | — | `Boolean` | Get eSIM not-supported popup state |

---

## Auth Interfaces

### SessionManager

Implement this to control how auth state is managed in your app.

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `isAuthenticated` | — | `Boolean` | Check if user is logged in |
| `getAccessToken` | — | `String` | Get current access token |
| `getRefreshToken` | — | `String` | Get current refresh token |
| `getAuthState` | — | `Auth` | Get full auth state |
| `save` | `auth: Auth` | `Unit` | Save auth state |
| `onAuthenticationFailed` | — | `Unit` | Called when the server rejects a token refresh; defaults to saving `Auth.Unauthenticated` |

### SecureStorageProvider

Implement this to control where tokens are stored.

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `secureSave` | `value: String, forKey: String` | `Unit` | Save encrypted value |
| `secureLoad` | `key: String, default: String` | `String` | Load encrypted value |
| `clearSecureStorage` | — | `Unit` | Clear all secure storage |
| `save` | `value: T, forKey: String` | `Unit` | Generic save (String, Boolean, Int, Long, Float) |
| `load` | `key: String, default: T` | `T` | Generic load |

### Auth (Sealed Interface)

```kotlin
Auth.Unauthenticated          // No active session

Auth.Authenticated(
    user: Customer,            // User profile
    expires: LocalDateTime,    // Token expiry
    accessToken: String,       // OAuth access token
    refreshToken: String       // OAuth refresh token
)
```

---

## Data Models

### Customer

| Field | Type | Description |
|-------|------|-------------|
| id | String | Unique ID |
| email | String? | Email address |
| firstName | String? | First name |
| lastName | String? | Last name |
| fullName | String? | Full name |
| phoneNumber | String? | Phone number |
| wallet | Double? | Wallet balance |
| walletCurrency | String? | Wallet currency |
| referralCode | String? | User's referral code (decodes `referral_code` or `unique_referral_code`) |
| acquisitionSource | String? | How the customer was acquired |
| signedInWithProvider | Boolean? | Social sign-in flag |
| preferredLanguage | String? | Language preference |
| preferredCurrency | String? | Currency preference |
| loyaltyProvider | String? | `kreds` or `mokafaa` |
| mokafaaEnrollment | MokafaaEnrollment? | Mokafaa enrolment state |
| receiveMarketingEmail | Boolean? | `null` = the API did not say |
| receiveMarketingPush | Boolean? | `null` = the API did not say |
| receiveAccountEmail | Boolean? | `null` = the API did not say |
| receiveAccountSms | Boolean? | `null` = the API did not say |
| receiveAccountPush | Boolean? | `null` = the API did not say |
| receivePurchaseEmail | Boolean? | `null` = the API did not say |
| receivePurchasePush | Boolean? | `null` = the API did not say |
| receiveViberMessages | Boolean? | `null` = the API did not say |

### Country

| Field | Type | Description |
|-------|------|-------------|
| name | String | Country name |
| code | String | Country code (e.g. "US") |
| flag | String | Flag emoji |
| slug | String | URL slug |
| destinations | List\<SupportedCountry\> | Supported countries in region |
| isRegion | Boolean | True if this is a region (not a single country) |
| fromPrice | String? | Starting price, server text verbatim |
| fromPriceValue | Double? | `fromPrice` parsed, for arithmetic (computed) |
| currency | String? | Currency code |
| currencyObject | CurrencyObject? | Currency details |

### Destination

| Field | Type | Description |
|-------|------|-------------|
| code | String? | Country code |
| name | String? | Country name |
| slug | String? | URL slug |
| region | String? | Region name |

### PackagePlan

| Field | Type | Description |
|-------|------|-------------|
| name | String | Package name |
| price | String | Full price, server text verbatim |
| convertedPrice | Double? | Price in the user's currency (still a `Double?`) |
| data | Double | Data in GB (-1 = unlimited) |
| country | Country | Associated country |
| currency | String | Currency code |
| currencyObject | CurrencyObject | Currency details |
| planType | String | Plan type |
| packageSlug | String | URL slug |
| validityDays | Long | Validity in days |
| packageTypeId | Long | Unique package ID |
| supportedCountries | List\<SupportedCountry\> | Countries covered |
| discountedPrice | String? | Discounted price, server text verbatim |
| earnPercentage | Double? | Kreds earn percentage |
| dataCap | String? | Fair usage data cap |
| throttleSpeed | String? | Throttled speed after cap |
| **Computed:** | | |
| isUnlimited | Boolean | True if data is -1 |
| purchasePrice | String | `discountedPrice ?: price` |
| priceValue | Double | `price` parsed |
| discountedPriceValue | Double? | `discountedPrice` parsed |
| purchasePriceValue | Double | `purchasePrice` parsed |
| isFreePurchase | Boolean | True if `purchasePriceValue` is 0 |
| hasDiscount | Boolean | True if discounted |

### AssignedEsim

| Field | Type | Description |
|-------|------|-------------|
| iccid | String | eSIM ICCID |
| name | String? | Custom name |
| country | Country? | Associated country |
| orderUUID | String? | Order reference |
| profile | EsimProfile? | eSIM profile details (nullable since 2.0) |
| assignedDate | String | Date assigned |
| packages | List\<PackageDetail\> | Active packages |
| dataUsageRemainingBytes | Double | Remaining data (bytes) |
| dataUsageRemainingGigabytes | Double | Remaining data (GB) |
| isArchived | Boolean | Archived flag |
| isAutoTopUp | Boolean | Auto top-up enabled |
| isPrimary | Boolean | Primary eSIM flag |
| isUniversal | Boolean | Universal eSIM flag |
| androidSha | Boolean | Android SHA support |
| orderNumber | String? | Order number |
| dateActivatedEpoch | Long? | Activation timestamp |
| dateExpiryEpoch | Long? | Expiry timestamp |
| daysLeftToExpiry | Int? | Days until expiry |
| smDpAddress | String? | SM-DP+ address, for direct install |
| activationCode | String? | Activation code, for direct install |
| qrCodeImageBase64 | String? | QR image, when `includeBase64QrCode = true` |
| esimProvider | String? | Provider name |
| **Computed:** | | |
| canInstallDirectly | Boolean | True when `smDpAddress` and `activationCode` are both present |
| hasUnlimitedPackage | Boolean | True when remaining GB is -1 |

### OrderDetail

| Field | Type | Description |
|-------|------|-------------|
| iccid | String? | eSIM ICCID |
| country | Country? | Country |
| qrCode | String? | QR code string |
| qrCodeImageBase64 | String? | QR code as base64 image |
| activationCode | String? | eSIM activation code |
| finalPrice | String | Final charged price, server text verbatim |
| orderDate | String | Purchase date |
| orderNumber | Int | Order number |
| orderStatus | String | Current status |
| orderType | String | "buy" or "top-up" |
| packageTypeId | Int | Package type |
| packageName | String | Package name |
| packageDataSize | Double | Data size |
| packageValidity | Int | Validity days |
| currency | String | Currency code |
| currencyObject | CurrencyObject | Currency details |
| price | String | Original price (`purchase_price`), server text verbatim |
| discountAmount | String | Discount applied, server text verbatim |
| discountCode | String | Promo code used |
| paymentMethod | PaymentMethod | How it was paid |
| loyaltyPointsEarned | LoyaltyPointsDetail? | Kreds earned |
| loyaltyPointsSpent | LoyaltyPointsDetail? | Kreds spent |
| **Computed:** | | |
| priceValue | Double | `price` parsed |
| finalPriceValue | Double | `finalPrice` parsed |
| discountAmountValue | Double | `discountAmount` parsed |

### PaymentResponse

| Field | Type | Description |
|-------|------|-------------|
| detail | String? | Error detail |
| transaction | Transaction? | Transaction info |

### Transaction

| Field | Type | Description |
|-------|------|-------------|
| uri | String? | Stripe intent secret or payment URL |
| orderId | String? | Order UUID |
| zeroCharge | Boolean | True if fully paid with Kreds |
| customerRef | String? | Customer reference |
| ephemeralKey | String? | Stripe ephemeral key |
| publishableKey | String? | Stripe publishable key |
| isIntent | Boolean | True if Stripe PaymentIntent |

### CheckoutCouponResponse

| Field | Type | Description |
|-------|------|-------------|
| valid | Boolean | Whether code is valid |
| detail | String? | Message |
| discount | String? | Discount description |
| percentage | Double? | Discount percentage |
| productType | String? | Product type restriction |

### CheckStockResponse

| Field | Type | Description |
|-------|------|-------------|
| stock | Boolean | In stock |
| packageInfo | PackagePlan? | Package details (nullable since 2.0) |
| promoCode | CheckoutCouponResponse | Active promo |

### KredsLoyaltyBalanceResponse

| Field | Type | Description |
|-------|------|-------------|
| totalLoyaltyPoints | Int | Total points |
| totalLoyaltyPointsDetail | LoyaltyPointsDetail | Detailed balance |

### LoyaltyPointsDetail

| Field | Type | Description |
|-------|------|-------------|
| amount | String? | Amount in currency, server decimal text verbatim — display it, do not do arithmetic on it |
| amountLocalCurrency | String? | Same amount converted to the customer's preferred currency, also verbatim text |
| amountLocalCurrencyCents | Int? | The preferred-currency amount in minor units, safe for arithmetic |
| currency | CurrencyObject | Currency info. Already the preferred currency — the server fills it from the accept-currency request header |
| original | LoyaltyPointsOriginal? | The pre-conversion USD amount, when the server converted |
| resolvedAmount | String | Computed. First non-empty of `amount`, `amountLocalCurrency`, `original.amountUSD`, else `"0.00"` |
| resolvedCurrencyIso | String | Computed. `currency.isoCode` |

### CurrencyObject

| Field | Type | Description |
|-------|------|-------------|
| symbol | String | e.g. "$" |
| isoCode | String | e.g. "USD" |

### PaymentMethod (Enum)

| Value | `displayName` | `shouldShowAmount` |
|-------|---------------|--------------------|
| STRIPE_INTENT | Stripe | true |
| STRIPE_CHECKOUT | Stripe | true |
| AGENT_PAYMENT | Agent Payment | true |
| COMPLIMENTARY | Complimentary | false |
| VOUCHER | Voucher | false |
| SPLIT_PAYMENT | Split Payment | true |
| PAY_WITH_POINTS | Pay with Points | true |
| UNKNOWN | Payment | false |

Decoding is total: any wire value the enum does not recognise becomes `UNKNOWN` rather than throwing, so a new backend payment method will not break an older app. The public `PaymentMethodSerializer` object implements this; it is wired up by the `@Serializable(with = …)` annotation and you never call it yourself.

### NotificationSettings

| Field | Type | Description |
|-------|------|-------------|
| type | String | Notification type |
| enabled | Boolean | Enabled flag |

### UserLocationResponse

| Field | Type | Description |
|-------|------|-------------|
| location | LocationDetails? | Location info |

### LocationDetails

| Field | Type | Description |
|-------|------|-------------|
| country | String? | Country name |
| countryCode | String? | Country code |
| city | String? | City |
| lat | Double? | Latitude |
| lon | Double? | Longitude |
| timezone | String? | Timezone |

### EsimProfile

| Field | Type | Description |
|-------|------|-------------|
| state | EsimProfileState? | ENABLED, DOWNLOADED, INSTALLED, DISABLED, DELETED, RELEASED, ERROR |
| activationCode | String? | Activation code |
| iccid | String? | ICCID |
| installed | Boolean | True if state is ENABLED/DOWNLOADED/INSTALLED/DISABLED (computed) |
| isDeleted | Boolean | True if state is DELETED (computed) |

### VisaRewardsResponse

| Field | Type | Description |
|-------|------|-------------|
| eligible | Boolean | User eligible |
| status | Int? | Status code |
| detail | String? | Detail message |
| redeemed | Boolean | Already redeemed |
| reward | String? | Reward description |
| used | Int? | Claims used (nullable since 2.0) |
| allowed | Int? | Total allowed (nullable since 2.0) |
| remaining | Int? | Remaining claims (nullable since 2.0) |
| remainingOrAllowed | Int? | `remaining ?: allowed` (computed) |
| validityDays | Int? | Reward validity |
| dataGB | Double? | Data reward amount |

### VoucherRedeemResponse

| Field | Type | Description |
|-------|------|-------------|
| redeemed | Boolean | Success flag |
| redirectUrl | String? | Redirect URL after redeem |
| orderUUID | String? | Extracted order ID (computed) |

### RatingApiResponse

| Field | Type | Description |
|-------|------|-------------|
| storeName | String | Store name |
| verdict | String | Summary verdict |
| reviewCount | Int | Review count |
| resultsCount | Int | Results count |
| rating | Double? | Average rating |
| reviews | List\<Review\>? | Individual reviews |
| stats | Stats? | Company totals and per-star counts |

### Review

| Field | Type | Description |
|-------|------|-------------|
| type | String? | Review type |
| typeLabel | String? | Human-readable type |
| rating | Int? | Star rating |
| title | String? | Review title |
| comments | String? | Review body |
| author | Author? | Author name and location |
| dateCreated | String? | Creation date |
| timeAgo | String? | Relative date |

### Faq

| Field | Type | Description |
|-------|------|-------------|
| question | String | FAQ question |
| answer | String | FAQ answer |

### ThemePage

| Field | Type | Description |
|-------|------|-------------|
| urlPath | String? | Path the theme applies to |
| featuredImage | ThemeImage? | Featured image (url + accent) |
| color | String? | Accent colour |

### ThemeDestination

| Field | Type | Description |
|-------|------|-------------|
| image | ThemeImage? | Destination image |
| gallery | List\<String\>? | Gallery image URLs |
| countryCode | String? | Country code the theme belongs to |

### OrdersPage

| Field | Type | Description |
|-------|------|-------------|
| orders | List\<OrderHistoryItem\> | Orders in this page |
| totalCount | Int | Total orders |
| hasMore | Boolean | More pages available |

### PackagesPage

| Field | Type | Description |
|-------|------|-------------|
| packages | List\<PackagePlan\> | Packages in this page |
| totalCount | Int | Total packages |
| promoCode | CheckoutCouponResponse? | Promo code attached to the page |

---

## Data Models — customer and profile

### CustomerDetails

The mutable customer payload. You build this yourself to pass into `PaymentsRepository.getPaymentIntent`.

| Field | Type | Description |
|-------|------|-------------|
| id | String? | Customer ID (`customer_id`) |
| email | String? | Email address |
| password | String? | Password, when the call creates an account |
| firstName | String? | Given name |
| lastName | String? | Family name |
| fullName | String? | Full name, when the backend supplies it pre-joined |
| phoneNumber | String? | Phone number in E.164 |
| referredBy | String? | Referral code the customer signed up under |
| newId | String? | Replacement customer ID, on a profile merge |
| newEmail | String? | Replacement email, when changing the address |
| marketingConsent | Boolean? | Marketing opt-in (`marketing_opt_in`) |
| loyaltyElection | String? | Loyalty programme elected at signup |

### ProfileResponse

Returned by `register`, `updateProfile` and `updateCustomerProfile`.

| Field | Type | Description |
|-------|------|-------------|
| id | String? | Customer ID |
| email | String? | Email address |
| detail | String? | Backend detail message |
| message | String? | Backend message |
| success | Boolean? | Whether the operation succeeded |
| updated | Boolean? | Whether an existing record was changed |
| referral | String? | Referral identifier |
| referralCode | String? | The customer's own referral code |
| mokafaa | MokafaaElection? | Whether the customer elected Mokafaa at registration |

### CustomerForgetPassword

Forgot-password request payload. Built internally by `AuthRepository.forgotPassword`.

| Field | Type | Description |
|-------|------|-------------|
| id | String? | Customer ID |
| email | String? | Email address to send the reset link to |

### CustomerForgetPasswordResponse

| Field | Type | Description |
|-------|------|-------------|
| id | String? | Customer ID |
| email | String? | Email the reset was sent to |
| detail | String? | Backend detail message |

### CustomerChangePassword

Change/reset password request payload. Built internally by `AuthRepository.changePassword` and `resetPassword`.

| Field | Type | Description |
|-------|------|-------------|
| id | String? | Customer ID |
| email | String? | Email address |
| token | String? | Password reset token (`password_reset_token`), for the reset flow |
| password | String? | Current password, for the change flow |
| newPassword | String | The new password. The only non-optional field |

### ChangePasswordResponse

| Field | Type | Description |
|-------|------|-------------|
| customer | Customer? | Updated profile (`customer_details`) |
| success | Boolean | Whether the password was changed (`password_reset`) |
| detail | String? | Backend detail message |

### UpdateCustomerPreferencesRequest

Built internally by `AuthRepository.updatePreferences`.

| Field | Type | Description |
|-------|------|-------------|
| preferredLanguage | String? | Preferred language code |
| preferredCurrency | String? | Preferred currency ISO code |

### VerifyEmailRequest

Built internally by `AuthRepository.verifyEmail`.

| Field | Type | Description |
|-------|------|-------------|
| email | String | Email address being verified |
| token | String | Verification token (`email_verification_token`) |
| orderUUID | String? | Order this verification belongs to, if any |

### VerifyEmailResponse

| Field | Type | Description |
|-------|------|-------------|
| email | String? | The verified email address |
| detail | String? | Backend detail message |
| isVerified | Boolean | Whether the address is now verified (`email_verified`) |

### DeleteProfileResponse

| Field | Type | Description |
|-------|------|-------------|
| deleted | Boolean | Whether the account was deleted |

### CountryCode

A phone-number country code. Standalone helper — no repository returns it. Parse a list you ship yourself with `CountryCode.getAllFrom(json)`, which returns an empty list rather than throwing if the JSON will not decode.

| Field | Type | Description |
|-------|------|-------------|
| id | String | Identifier |
| name | String | Country name |
| emoji | String | Flag emoji (`flag`) |
| isoCode | String | ISO country code (`code`) |
| dialCode | String | International dialling prefix |
| pattern | String | Local number format pattern |
| limit | Int | Maximum national number length |

---

## Data Models — tokens

These describe the OAuth2 exchange the SDK performs for you. Token handling is internal; you will not normally construct or receive these.

### GetTokenResponse

| Field | Type | Description |
|-------|------|-------------|
| accessToken | String? | OAuth2 access token |
| expiresIn | Int | Lifetime in seconds. Defaults to `3600` |
| tokenType | String? | Token type, e.g. `Bearer` |
| scope | String? | Granted scopes |
| refreshToken | String? | Refresh token |
| user | Customer? | The authenticated profile |
| error | String? | Error code, when the exchange failed |
| detail | String? | Backend detail message |
| description | String? | Human-readable error (`error_description`) |

### GetTokenIntrospectResponse

Token introspection result. Public, but not reachable through any repository method in 2.0.0.

| Field | Type | Description |
|-------|------|-------------|
| exp | Int? | Expiry as a Unix timestamp |
| scope | String? | Granted scopes |
| isActive | Boolean | Whether the token is still active (`active`) |
| username | String? | Subject of the token |
| clientId | String? | OAuth2 client the token was issued to |

---

## Data Models — catalogue and eSIM

### SupportedCountry

A minimal country reference inside a region or package.

| Field | Type | Description |
|-------|------|-------------|
| name | String | Country name (`country_name`) |
| code | String | ISO country code (`country_code`) |

### PackageDetail

Per-package usage and validity on an `AssignedEsim`. Byte and gigabyte counters are `Double`.

| Field | Type | Description |
|-------|------|-------------|
| status | String | Package status |
| packageID | String? | Package identifier |
| packageTypeID | Long? | Package type identifier |
| dateExpiryEpoch | Long? | Expiry, Unix epoch seconds |
| dateActivatedEpoch | Long? | Activation, Unix epoch seconds |
| dateTerminatedEpoch | Long? | Termination, Unix epoch seconds |
| supportedCountries | List\<SupportedCountry\> | Countries this package covers |
| dateCreatedUTC | String? | Creation timestamp, UTC text |
| dateCreatedEpoch | Long | Creation, Unix epoch seconds |
| packageCountryName | String | Country the package was sold for |
| packageCountryCode | String? | ISO code of that country |
| voiceUsageRemainingSeconds | Long | Voice seconds remaining |
| dataUsageRemainingGigabytes | Double | Data remaining in GB. `-1.0` means unlimited |
| dataUsageRemainingBytes | Double | Data remaining in bytes |
| dataAllowanceGigabytes | Double | Total allowance in GB. `-1.0` means unlimited |
| dataAllowanceBytes | Double | Total allowance in bytes |
| dataUsedBytes | Double? | Data consumed in bytes (`data_usage_bytes`) |
| smsUsageRemainingNums | Int | SMS remaining |
| windowActivationStartEpoch | Long | Activation window opens, epoch seconds |
| windowActivationEndEpoch | Long | Activation window closes, epoch seconds |
| windowActivationStartUtc | String? | Activation window opens, UTC text |
| windowActivationEndUtc | String? | Activation window closes, UTC text |
| timeAllowanceSeconds | Double | Validity in seconds |
| timeAllowanceDays | Double | Validity in days |
| statusMessage | String | Human-readable status |
| hasUnlimitedPackage | Boolean | Computed. True when allowance or remaining GB is `-1.0` |

### EsimInfo

Compact eSIM metadata carried on an order.

| Field | Type | Description |
|-------|------|-------------|
| assignedDate | String | When the eSIM was assigned |
| iccid | String | ICCID |
| matchingId | String | SM-DP+ matching ID, the activation code's second half |
| premium | Boolean | Whether this is a premium profile |
| smDpAddress | String | SM-DP+ server address |
| androidSha | Boolean | Whether the profile supports Android direct install |
| country | String | Country the eSIM was issued for |
| esimName | String? | Customer-assigned nickname |
| isUniversal | Boolean | Whether the profile is a universal (multi-region) one |

### EsimProfileState (Enum)

State of an eSIM profile on the SM-DP+ platform, as used by `EsimProfile.state`.

| Value | Meaning |
|-------|---------|
| ENABLED | Installed and active on the device |
| DOWNLOADED | Downloaded to the device, not yet enabled |
| INSTALLED | Installed on the device |
| DISABLED | Installed but switched off |
| DELETED | Removed from the device |
| RELEASED | Released by the platform, not yet downloaded |
| ERROR | The profile is in an error state |

`EsimProfile.installed` is true for ENABLED, DOWNLOADED, INSTALLED and DISABLED. `EsimProfile.isDeleted` is true only for DELETED.

### ProfileReusePolicy

How many times an eSIM profile may be re-downloaded. Reached via `EsimProfile.policy`.

| Field | Type | Description |
|-------|------|-------------|
| type | String? | Reuse policy type (`reuse_type`) |
| count | Int? | Maximum number of reuses (`max_count`) |

### QrCode

| Field | Type | Description |
|-------|------|-------------|
| imageBase64 | String | QR code PNG as base64 |
| imageUrl | String | Hosted URL for the same QR code |

### RestrictedCountry

A country with purchase restrictions. Public, but not returned by any repository method in 2.0.0.

| Field | Type | Description |
|-------|------|-------------|
| countryCode | String | ISO code of the restricted country |
| restrictionType | RestrictionType | Whether the restriction is global or local |
| restrictedFor | List\<RestrictedFor\>? | Countries the restriction applies to |

### RestrictedFor

| Field | Type | Description |
|-------|------|-------------|
| countryCode | String | ISO code of the affected country |
| countryName | String | Name of the affected country |

### RestrictionType (Enum)

| Value | Wire value | Meaning |
|-------|-----------|---------|
| GLOBAL | `global` | Restricted everywhere |
| LOCAL | `local` | Restricted only for the countries in `restrictedFor` |

---

## Data Models — orders and payments

### OrderHistoryItem

A summary order for history lists. Its money fields are server-verbatim `String`s and, unlike `OrderDetail`, have **no** `…Value: Double` companions — parse them yourself if you need arithmetic.

| Field | Type | Description |
|-------|------|-------------|
| esim | EsimInfo | eSIM issued for this order |
| orderNumber | Int | Human-facing order number |
| orderUUID | String | Order UUID, the key for `getOrderDetails` |
| orderType | String | `buy` or `top-up` |
| packageId | String | Package identifier |
| packageName | String | Package display name |
| packageTypeId | Int | Package type identifier |
| finalPrice | String | Amount charged, verbatim decimal text |
| purchasePrice | String | List price before discount, verbatim decimal text |
| discountAmount | String | Discount applied, verbatim decimal text |
| discountCode | String | Promo code used |
| purchaseDate | String | Purchase timestamp |
| purchaseCurrency | String | Currency ISO code |
| purchaseCurrencyObject | CurrencyObject | Symbol and ISO code for rendering |
| paymentStatus | String | Payment status |
| paymentMethod | PaymentMethod | How it was paid. Unrecognised values decode to `UNKNOWN` |
| country | Country? | Destination country |
| loyaltyPointsEarned | LoyaltyPointsDetail? | Points earned (`points_earned`) |
| loyaltyPointsSpent | LoyaltyPointsDetail? | Points spent (`points_spent`) |
| user | String | User reference |
| conversionTracked | Boolean | Whether `trackOrder` has already run for this order |
| purchaseCountry | PurchaseCountry? | Country the purchase was made from |

### PurchaseCountry

| Field | Type | Description |
|-------|------|-------------|
| iso | String | Two-letter ISO code |
| name | String | Country name |
| iso3 | String | Three-letter ISO code |
| flag | String | Flag emoji |
| isRegion | Boolean | Whether this is a region rather than a single country |

### OrderInfo

A standalone order view. Public, but not returned by any repository method in 2.0.0 — use `OrderDetail` from `OrdersRepository.getOrderDetails`. Money fields are verbatim `String`s with no `…Value` companions.

| Field | Type | Description |
|-------|------|-------------|
| detail | String? | Backend detail message |
| customer | Customer | Purchasing customer |
| esim | EsimInfo | eSIM issued |
| qrCode | QrCode | QR code for installation |
| iccid | String | ICCID |
| orderType | String | `buy` or `top-up` |
| orderUuid | String | Order UUID |
| packageId | String | Package identifier |
| packageName | String | Package display name |
| packageTypeId | Int | Package type identifier |
| discountAmount | String | Discount applied, verbatim decimal text |
| discountCode | String | Promo code used |
| finalPrice | String | Amount charged, verbatim decimal text |
| purchasePrice | String | List price, verbatim decimal text |
| purchaseCountry | Country | Country the purchase was made from |
| purchaseCurrency | String | Currency ISO code |
| purchaseDate | String | Purchase timestamp |

### PaymentRequest

The payload you build for `PaymentsRepository.getPaymentIntent`. Use the nested constants rather than raw strings: `PaymentRequest.Type.BUY` / `TOP_UP`, and `PaymentRequest.Method.STRIPE_INTENT` / `STRIPE_CHECKOUT`.

| Field | Type | Description |
|-------|------|-------------|
| type | String | `buy` or `top-up`. Required |
| iccid | String? | Target eSIM for a top-up. Null for a new purchase |
| customer | CustomerDetails | Purchasing customer. Required |
| packageTypeId | Int | Package being bought. Required. Note `PackagePlan.packageTypeId` is a `Long` — narrow it |
| paymentMethod | String | `stripe_intent` or `stripe_checkout`. Required |
| autoTopUp | Boolean | Enable automatic top-up on the resulting eSIM. Required |
| savePaymentMethod | Boolean | Store the card for reuse. Required |
| loyaltyPointsAmount | Double? | Kreds value to apply |
| loyaltyProvider | String? | `kreds` or `mokafaa`. See `LoyaltyProvider` |
| loyaltyPointsToUse | Int? | Mokafaa points to burn |
| couponId | String? | Promo code to apply |

### CheckoutCouponRequest

Promo code payload. Built internally by `PromoCodeRepository.addPromoCode` — you pass the code as a `String`.

| Field | Type | Description |
|-------|------|-------------|
| code | String? | The promo code (`promo_code`) |

---

## Data Models — loyalty and Kreds

Every amount in these models is a server-verbatim decimal `String` (`"12.50"`). None of them carries a `…Value: Double` companion, so parse with `toDoubleOrNull()` before doing arithmetic. Point counts (`requestedCents`, `appliedCents`) are `Int` minor units and are safe to compute with directly.

### KredsQuoteRequest

Built internally by `getKredsQuote` and `getMokafaaQuote`.

| Field | Type | Description |
|-------|------|-------------|
| packageTypeId | Int | Package being quoted |
| loyaltyPointsAmount | Double? | Kreds value to apply |
| loyaltyProvider | String? | `kreds` or `mokafaa` |
| loyaltyPointsToUse | Int? | Mokafaa points to burn |

### KredsQuoteResponse

| Field | Type | Description |
|-------|------|-------------|
| packageTypeId | Int? | Package quoted |
| currency | CurrencyObject? | Order currency |
| preferredCurrency | CurrencyObject? | Customer's preferred currency |
| pricing | KredsQuotePricing | Price breakdown. Always present |
| points | KredsQuotePoints | Points breakdown. Defaults to an empty breakdown |
| notices | List\<QuoteNotice\>? | Server-side warnings to surface to the user |

### QuoteNotice

| Field | Type | Description |
|-------|------|-------------|
| code | String | Machine-readable notice code |
| message | String | Human-readable text |

### KredsQuotePricing

| Field | Type | Description |
|-------|------|-------------|
| orderCurrency | KredsQuoteOrderCurrency | Breakdown in the order's own currency. Always present |
| usd | KredsQuoteUsdPricing? | The same breakdown in USD |
| preferredCurrency | KredsQuotePreferredPricing? | Total in the customer's preferred currency |

### KredsQuoteOrderCurrency

| Field | Type | Description |
|-------|------|-------------|
| exchangeRateToUsd | String? | Rate used to reach USD, verbatim text |
| subtotal | String? | Before discounts, verbatim text |
| packageDiscount | String? | Package-level discount, verbatim text |
| promoDiscount | String? | Promo code discount, verbatim text |
| pointsApplied | String? | Value covered by points, verbatim text |
| total | String | Amount payable, verbatim text. Always present |
| currency | CurrencyObject? | Currency these amounts are in |

### KredsQuoteUsdPricing

Same shape as `KredsQuoteOrderCurrency` without the exchange rate.

| Field | Type | Description |
|-------|------|-------------|
| subtotal | String? | Before discounts, verbatim text |
| packageDiscount | String? | Package-level discount, verbatim text |
| promoDiscount | String? | Promo code discount, verbatim text |
| pointsApplied | String? | Value covered by points, verbatim text |
| total | String | Amount payable in USD, verbatim text. Always present |
| currency | CurrencyObject? | Currency, USD |

### KredsQuotePreferredPricing

| Field | Type | Description |
|-------|------|-------------|
| total | String | Amount payable in the preferred currency, verbatim text. Always present |
| currency | CurrencyObject? | The preferred currency |

### KredsQuotePoints

| Field | Type | Description |
|-------|------|-------------|
| requestedCents | Int? | Points value the client asked to apply, in minor units |
| appliedCents | Int? | Points value actually applied, in minor units. May be lower than requested |
| appliedValue | KredsQuoteValue? | Applied value in the order currency |
| appliedValueUsd | KredsQuoteValue? | Applied value in USD |
| appliedValuePreferred | KredsQuoteValue? | Applied value in the preferred currency |

### KredsQuoteValue

| Field | Type | Description |
|-------|------|-------------|
| amount | String | Verbatim decimal text. Always present |
| currency | CurrencyObject | Currency. Defaults to an empty `CurrencyObject` |

### LoyaltyPointsOriginal

The pre-conversion amount behind a `LoyaltyPointsDetail`.

| Field | Type | Description |
|-------|------|-------------|
| amountUSD | String | USD amount, verbatim text (`amount_usd`) |
| currency | CurrencyObject | Currency, USD |

### LoyaltyProvider

Not a model — a constant holder. `LoyaltyProvider.KREDS` is `"kreds"`, `LoyaltyProvider.MOKAFAA` is `"mokafaa"`. Use these for `PaymentRequest.loyaltyProvider`.

---

## Data Models — Mokafaa

### MokafaaOtpInitiateRequest

Built internally by `initiateMokafaaOtp`. Its nested constants are the values you pass: `MokafaaOtpInitiateRequest.Purpose.ENROLLMENT` / `CHECKOUT`, and `MokafaaOtpInitiateRequest.Platform.ANDROID`.

| Field | Type | Description |
|-------|------|-------------|
| purpose | String | `enrollment` or `checkout` |
| platform | String | `android` |

### MokafaaOtpInitiateResponse

| Field | Type | Description |
|-------|------|-------------|
| sessionId | String | Session to pass to `validateMokafaaOtp` |
| expiresAt | String | When the session expires — drive your countdown from this, not a fixed timer |
| maskedPhoneNumber | String? | Masked number the SMS went to, for display |

### MokafaaOtpValidateRequest

Built internally by `validateMokafaaOtp`.

| Field | Type | Description |
|-------|------|-------------|
| sessionId | String | Session from `initiateMokafaaOtp` |
| otp | String | Code the customer entered |
| points | Int? | Points to burn. Required for checkout, omitted for enrollment |
| packageTypeId | Int? | Package the burn applies to |

### MokafaaOtpValidateResponse

Nested constants: `MokafaaOtpValidateResponse.Status.CONFIRMED` / `REVERSED` / `FAILED`.

| Field | Type | Description |
|-------|------|-------------|
| status | String | `confirmed`, `reversed` or `failed` |
| pointsRedeemed | Int? | Points actually burned |

### MokafaaEnrollment

Enrollment state on the customer profile (`Customer.mokafaaEnrollment`). Nested constants: `MokafaaEnrollment.State.COMPLETED` / `PENDING` / `EXPIRED` / `ELECTED` / `NOT_ELECTED`.

| Field | Type | Description |
|-------|------|-------------|
| state | String | One of the `State` constants |
| sessionExpiresAt | String? | When a pending enrollment session lapses |

### MokafaaElection

| Field | Type | Description |
|-------|------|-------------|
| elected | Boolean? | Whether the customer opted into Mokafaa at registration |

---

## Data Models — rewards, vouchers, content

### VisaRewardsIframeResponse

Returned by `VisaRewardsRepository.getIframe`.

| Field | Type | Description |
|-------|------|-------------|
| created | Boolean | Whether a new session was created |
| token | String? | Token to pass to `verify` |
| aliasId | String? | Alias identifier |
| iframeUrl | String? | URL to load in a WebView |
| correlationId | String? | Correlation ID for support |
| status | Int? | Status code |
| eligible | Boolean | Whether the customer is eligible |
| redeemed | Boolean | Whether the reward is already redeemed |

### VoucherRedeemRequest

Built internally by `VouchersRepository.redeemVoucher` — you pass the code as a `String`.

| Field | Type | Description |
|-------|------|-------------|
| voucherCode | String | The voucher code |

### ThemeImage

| Field | Type | Description |
|-------|------|-------------|
| url | String | Image URL |
| accent | String? | Accent colour sampled from the image |

### DestinationFaqResponse

The full FAQ document for a destination. `FaqAndSupportRepository.fetchDestinationFaqs` returns just the `faqs` list.

| Field | Type | Description |
|-------|------|-------------|
| slug | String | Destination slug |
| name | String | Destination name |
| language | String | Language the FAQs are written in |
| faqs | List\<Faq\> | The questions and answers |

### Author

| Field | Type | Description |
|-------|------|-------------|
| name | String? | Reviewer name |
| location | String? | Reviewer location |

### Stats

| Field | Type | Description |
|-------|------|-------------|
| company | CompanyStats? | Company-wide totals |
| ratings | Ratings? | Per-star counts |

### CompanyStats

| Field | Type | Description |
|-------|------|-------------|
| reviewCount | Int | Total reviews |
| averageRating | String | Average rating as verbatim text, not a number |

### Ratings

| Field | Type | Description |
|-------|------|-------------|
| four | Int? | Number of 4-star reviews (JSON key `"4"`) |
| five | Int? | Number of 5-star reviews (JSON key `"5"`) |

---

## Data Models — errors

### ApiErrorResponse

The standard error body. The SDK parses it for you and surfaces the text as an exception message; you rarely decode it yourself.

| Field | Type | Description |
|-------|------|-------------|
| detail | String? | Detail message |
| error | String? | Error code |
| message | String? | Human-readable message |

### IframeRequest

Vendor payload for an iframe session. Built internally.

| Field | Type | Description |
|-------|------|-------------|
| vendor | String? | Vendor identifier |
