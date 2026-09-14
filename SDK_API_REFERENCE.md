# eSimplified Android SDK — API Reference

Documents `io.github.esimplified:android-sdk:2.0.0`.

Upgrading from 1.x? Start with [Migrating from 1.x to 2.0](README.md#migrating-from-1x-to-20) in the README — money fields changed type, and the compiler does not catch every call site.

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
    clientId: String,
    clientSecret: String,
    apiVersion: String = "v2",
    awsWafToken: String = "",
    enableLogging: Boolean = false,
    customHeadersProvider: (() -> Map<String, String>)? = null,
    enableCaching: Boolean = true,
    defaultCacheTtlSeconds: Long = 3600
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

### EsimplifiedSdk.clearAllCaches()

Drops every cached read. Call it on logout.

### EsimplifiedSdk.sessionManager

The active `SessionManager`, for reading auth state outside a repository.

---

## Reads, caching and errors

Cached reads take two optional arguments, omitted from the tables below for brevity:

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `forceRefresh: Boolean` | `false` (`true` on `getLoyaltyBalance`) | Skip the cache and go to the network |
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

All repository functions are `suspend` unless noted. Inject via Koin:

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
| `logout` | — | `Unit` | End session |

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
| `getArchivedEsims` | same as `getEsims` | `List<AssignedEsim>` | Archived eSIMs only |
| `getEsimByIccid` | `iccid: String, includeBase64QrCode: Boolean = false` | `AssignedEsim` | Get one eSIM from `customer/esims/{iccid}/details/` |
| `updateEsim` | `iccid: String, name: String? = null, isAutoTopUp: Boolean? = null, isArchived: Boolean? = null, isPrimary: Boolean? = null` | `Unit` | Update eSIM settings. **Throws if the server rejects the write** |
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
| `getKredsQuote` | `packageTypeId: Int, loyaltyPointsAmount: Double` | `KredsQuoteResponse` | Get pricing quote with Kreds |
| `getMokafaaQuote` | `packageTypeId: Int, loyaltyPointsToUse: Int` | `KredsQuoteResponse` | Get pricing quote with Mokafaa points |
| `initiateMokafaaOtp` | `purpose: String, platform: String = "android"` | `MokafaaOtpInitiateResponse` | Start a Mokafaa OTP session (`purpose`: `enrollment` or `checkout`); countdown should be driven by `expiresAt` |
| `validateMokafaaOtp` | `sessionId: String, otp: String, points: Int? = null, packageTypeId: Int? = null` | `MokafaaOtpValidateResponse` | Validate the SMS OTP; `points` required for checkout, omitted for enrollment |

Mokafaa methods throw `LoyaltyApiException(httpCode, message)` on HTTP errors — `message` is the backend error verbatim, `httpCode` lets callers branch on 400/401/503.

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
| amount | String? | Amount in currency |
| currency | CurrencyObject | Currency info |
| resolvedAmount | String | Best available amount (computed) |
| resolvedCurrencyIso | String | Currency ISO code (computed) |

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
