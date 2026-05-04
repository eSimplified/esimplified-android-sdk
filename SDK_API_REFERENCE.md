# eSimplified Android SDK — API Reference

## Initialization

### EsimSdk.initialize()

```kotlin
EsimSdk.initialize(
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
    customHeadersProvider: (() -> Map<String, String>)? = null
)
```

### SdkEnvironment

| Value | Description |
|-------|-------------|
| `STAGING` | `https://{clientName}.stage.esimplified.io` |
| `PRODUCTION` | `https://{clientName}.live.esimplified.io` |

### EsimSdk.koinModule()

Returns a Koin `Module` containing all SDK dependencies. Add to your Koin setup:

```kotlin
startKoin {
    modules(EsimSdk.koinModule(), yourAppModule)
}
```

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
| `register` | `email: String, password: String, firstName: String, lastName: String, phoneNumber: String, marketingConsent: Boolean, referredBy: String? = null` | `ProfileResponse` | Create new account |

#### Password Management

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `forgotPassword` | `email: String` | `CustomerForgetPasswordResponse` | Send password reset email |
| `changePassword` | `currentPassword: String, newPassword: String` | `ChangePasswordResponse` | Change password (authenticated) |
| `resetPassword` | `email: String, token: String, newPassword: String` | `ChangePasswordResponse` | Reset password with token |

#### Profile & Session

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getUser` | — | `Customer?` | Get current user profile |
| `updateProfile` | `email: String, firstName: String?, lastName: String?, phoneNumber: String?, password: String` | `ProfileResponse` | Update user profile |
| `updatePreferences` | `preferredLanguage: String?, preferredCurrency: String?` | `Customer` | Update language/currency prefs |
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
| `getUserLocation` | — | `UserLocationResponse` | Get user's location by IP |

---

### PackagesRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getPackages` | `destination: Destination` | `List<PackagePlan>` | Get eSIM packages for a destination |
| `getTopUpPackages` | `iccid: String` | `List<PackagePlan>` | Get top-up packages for an existing eSIM |
| `checkStock` | `packageTypeId: Int` | `CheckStockResponse` | Check package availability |
| `getPackageRating` | — | `RatingApiResponse` | Get store ratings |

---

### EsimRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getEsims` | — | `List<AssignedEsim>` | Get all user's eSIMs |
| `getEsimByIccid` | `iccid: String` | `AssignedEsim` | Get specific eSIM |
| `updateEsim` | `iccid: String, name: String? = null, isAutoTopUp: Boolean? = null, isArchived: Boolean? = null` | `Unit` | Update eSIM settings |

---

### OrdersRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getOrderHistory` | — | `List<OrderHistoryItem>` | Get all orders |
| `getOrderHistory` | `withLoyaltyPoints: Boolean` | `List<OrderHistoryItem>` | Get orders filtered by loyalty points |
| `getOrderDetails` | `orderUuid: String` | `OrderDetail` | Get detailed order info |
| `trackOrder` | `orderUuid: String` | `Unit` | Track an order |

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
| `getLoyaltyBalance` | — | `KredsLoyaltyBalanceResponse` | Get Kreds points balance |
| `getKredsQuote` | `packageTypeId: Int, loyaltyPointsAmount: Double` | `KredsQuoteResponse` | Get pricing quote with Kreds |

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
| referralCode | String? | User's referral code |
| signedInWithProvider | Boolean? | Social sign-in flag |
| preferredLanguage | String? | Language preference |
| preferredCurrency | String? | Currency preference |

### Country

| Field | Type | Description |
|-------|------|-------------|
| name | String | Country name |
| code | String | Country code (e.g. "US") |
| flag | String | Flag emoji |
| slug | String | URL slug |
| destinations | List\<SupportedCountry\> | Supported countries in region |
| isRegion | Boolean | True if this is a region (not a single country) |
| fromPrice | Double? | Starting price |
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
| price | Double | Full price |
| data | Double | Data in GB (-1 = unlimited) |
| country | Country | Associated country |
| currency | String | Currency code |
| currencyObject | CurrencyObject | Currency details |
| planType | String | Plan type |
| packageSlug | String | URL slug |
| validityDays | Long | Validity in days |
| packageTypeId | Long | Unique package ID |
| supportedCountries | List\<SupportedCountry\> | Countries covered |
| discountedPrice | Double? | Discounted price |
| earnPercentage | Double? | Kreds earn percentage |
| dataCap | String? | Fair usage data cap |
| throttleSpeed | String? | Throttled speed after cap |
| **Computed:** | | |
| isUnlimited | Boolean | True if data is -1 |
| purchasePrice | Double | Discounted or full price |
| hasDiscount | Boolean | True if discounted |

### AssignedEsim

| Field | Type | Description |
|-------|------|-------------|
| iccid | String | eSIM ICCID |
| name | String? | Custom name |
| country | Country? | Associated country |
| orderUUID | String? | Order reference |
| profile | EsimProfile | eSIM profile details |
| assignedDate | String | Date assigned |
| packages | List\<PackageDetail\> | Active packages |
| dataUsageRemainingBytes | Double | Remaining data (bytes) |
| dataUsageRemainingGigabytes | Double | Remaining data (GB) |
| isArchived | Boolean | Archived flag |
| isAutoTopUp | Boolean | Auto top-up enabled |

### OrderDetail

| Field | Type | Description |
|-------|------|-------------|
| iccid | String? | eSIM ICCID |
| country | Country | Country |
| qrCode | String? | QR code string |
| qrCodeImageBase64 | String? | QR code as base64 image |
| activationCode | String? | eSIM activation code |
| finalPrice | Double | Final charged price |
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
| price | Double | Original price |
| discountAmount | Double | Discount applied |
| discountCode | String | Promo code used |
| paymentMethod | PaymentMethod | How it was paid |
| loyaltyPointsEarned | LoyaltyPointsDetail? | Kreds earned |
| loyaltyPointsSpent | LoyaltyPointsDetail? | Kreds spent |

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
| packageInfo | PackagePlan | Package details |
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

| Value | Display Name |
|-------|-------------|
| STRIPE_INTENT | Credit Card |
| STRIPE_CHECKOUT | Credit Card |
| AGENT_PAYMENT | Agent Payment |
| COMPLIMENTARY | Complimentary |
| VOUCHER | Voucher |
| SPLIT_PAYMENT | Split Payment |
| PAY_WITH_POINTS | Paid with Kreds |
| UNKNOWN | Unknown |

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
| allowed | Int | Total allowed |
| remaining | Int | Remaining claims |
| validityDays | Int? | Reward validity |
| dataGB | Double? | Data reward amount |

### VoucherRedeemResponse

| Field | Type | Description |
|-------|------|-------------|
| redeemed | Boolean | Success flag |
| redirectUrl | String? | Redirect URL after redeem |
| orderUUID | String? | Extracted order ID (computed) |
