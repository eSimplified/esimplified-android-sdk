# eSIMplified Android SDK

Kotlin SDK for integrating the eSIMplified eSIM platform into Android applications. Provides typed repository interfaces for authentication, eSIM management, package browsing, orders, payments, and more. All networking, authentication, and token management are handled internally -- consuming apps interact only with clean Kotlin interfaces.

**Coordinates:** `io.github.esimplified:android-sdk:1.0.2`

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
    implementation("io.github.esimplified:android-sdk:1.0.2")
}
```

Maven Central is included by default in all Gradle projects. No changes to `settings.gradle.kts` required.

## Quick Start

### 1. Initialize the SDK

Call this once in your `Application.onCreate()`:

```kotlin
EsimplifiedSdk.initialize(
    context = this,
    config = SdkConfig(
        environment = SdkEnvironment.PRODUCTION,  // or STAGING
        clientName = "yourcompany",                // your registered brand name
        clientId = "your-client-id",               // OAuth2 client ID
        clientSecret = "your-client-secret",       // OAuth2 client secret
        awsWafToken = "your-waf-token",            // AWS WAF validation token
        enableLogging = BuildConfig.DEBUG,
    )
)
```

The SDK constructs API URLs automatically from `clientName` and `environment`:
- **Staging:** `https://{clientName}.stage.esimplified.io`
- **Production:** `https://{clientName}.live.esimplified.io`

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
    environment: SdkEnvironment,                         // STAGING or PRODUCTION
    clientName: String,                                  // your brand name (used to build API URL)
    apiVersion: String = "v2",                           // API version path segment
    clientId: String,                                    // OAuth2 client ID
    clientSecret: String,                                // OAuth2 client secret
    awsWafToken: String = "",                            // AWS WAF validation token
    enableLogging: Boolean = false,                      // enable OkHttp request/response logging
    customHeadersProvider: (() -> Map<String, String>)?, // optional extra headers per request
)
```

## SDK Structure

```
sdk/src/main/java/io/esimplified/sdk/
|-- EsimplifiedSdk.kt                    # SDK entry point (initialize, koinModule)
|-- SdkConfig.kt                          # Configuration data class
|-- SdkEnvironment.kt                     # STAGING / PRODUCTION enum
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
|   +-- impl/                             # Internal implementations (not public API)
+-- di/
    +-- SdkModule.kt                      # Koin module wiring all dependencies
```

## All Models

Every model is a `@Serializable` data class in `io.esimplified.sdk.model`.

| Model | Description |
|---|---|
| `Customer` | Authenticated user profile (id, email, name, phone, wallet, referral code, preferences) |
| `CustomerDetails` | Mutable customer fields for registration and profile updates |
| `CustomerSignIn` | Login request payload (email + password) |
| `CustomerForgetPassword` | Forgot password request (email) |
| `CustomerForgetPasswordResponse` | Forgot password API response |
| `CustomerChangePassword` | Change/reset password request payload |
| `Country` | Destination country (name, code, flag, slug, supported countries, pricing) |
| `CountryCode` | Phone number country code (dial code, pattern, emoji flag) |
| `SupportedCountry` | Minimal country reference (name + code) within a region/package |
| `Destination` | Query parameters for fetching packages (code, name, slug, region) |
| `PackagePlan` | eSIM data plan (name, price, data GB, validity, country, networks, discounts) |
| `PackageDetail` | Detailed package info with activation status and expiry |
| `CheckStockResponse` | Stock availability check result |
| `EsimInfo` | Basic eSIM metadata (ICCID, matching ID, SM-DP+ address) |
| `AssignedEsim` | Full eSIM assigned to a customer (ICCID, packages, balance, settings) |
| `EsimProfile` | eSIM profile state from the SM-DP+ platform |
| `EsimProfileState` | Enum: ENABLED, DOWNLOADED, INSTALLED, DISABLED, DELETED, RELEASED, ERROR |
| `EsimRequest` | Request parameters for eSIM list queries |
| `EsimPackageListRequest` | Request for eSIM-specific package list |
| `OrderDetail` | Full order with pricing, eSIM profile, QR code, payment info, loyalty points |
| `OrderHistoryItem` | Summary order for history lists |
| `OrderInfo` | Order info with customer details and QR code |
| `OrderRequest` | Order query parameters |
| `PaymentRequest` | Payment intent creation payload (package, customer, payment method, loyalty points) |
| `PaymentMethod` | Enum: STRIPE_INTENT, GOOGLE_PAY, FREE, WALLET, LOYALTY |
| `CurrencyObject` | Currency with symbol and ISO code |
| `QrCode` | QR code image (base64 + URL) |
| `NotificationSettings` | Notification preference (type + enabled flag) |
| `LoyaltyPointsDetail` | Loyalty points earned/spent on an order |
| `KredsQuoteRequest` | Request for Kreds-to-discount quote |
| `KredsQuoteResponse` | Quote result with pricing and points breakdown |
| `VisaRewardsIframeResponse` | Visa rewards verification iframe URL and token |
| `VisaRewardsResponse` | Visa rewards eligibility, status, and reward details |
| `RewardActivationRequest` | Reward activation payload |
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
| `RatingApiResponse` | App store rating data |
| `ApiErrorResponse` | Standardized API error (detail, error, message) |
| `SearchBody` | Search query payload |
| `IframeRequest` | Iframe vendor request |
| `BaseResponse<T>` | Paginated response wrapper (count, next, previous, results) |
| `ProfileReusePolicy` | eSIM profile reuse policy |

## All Repository Methods

All repositories are provided as Koin singletons. Inject them by interface type.

### AuthRepository

Authentication, registration, password management, profile operations, and session control.

| Method | Signature | Description |
|---|---|---|
| `login` | `suspend fun login(email: String, password: String): Customer` | Authenticate with email/password, persist session |
| `loginWithRefreshToken` | `suspend fun loginWithRefreshToken(refreshToken: String): Customer` | Re-authenticate using a stored refresh token |
| `signInWithGoogle` | `suspend fun signInWithGoogle(email, firstName, lastName, fullName, phoneNumber, providerAccountId, idToken): Customer` | Authenticate via Google Sign-In |
| `register` | `suspend fun register(email, password, firstName, lastName, phoneNumber, marketingConsent, referredBy?): ProfileResponse` | Create a new customer account |
| `forgotPassword` | `suspend fun forgotPassword(email: String): CustomerForgetPasswordResponse` | Request a password reset email |
| `changePassword` | `suspend fun changePassword(currentPassword: String, newPassword: String): ChangePasswordResponse` | Change password for authenticated user |
| `resetPassword` | `suspend fun resetPassword(email: String, token: String, newPassword: String): ChangePasswordResponse` | Reset password using email token |
| `verifyEmail` | `suspend fun verifyEmail(email: String, token: String, orderUUID: String?): VerifyEmailResponse` | Verify email address with token |
| `deleteProfile` | `suspend fun deleteProfile(): DeleteProfileResponse` | Delete the authenticated user's account |
| `getUser` | `suspend fun getUser(): Customer?` | Fetch the current authenticated user profile |
| `updatePreferences` | `suspend fun updatePreferences(preferredLanguage: String?, preferredCurrency: String?): Customer` | Update language/currency preferences |
| `updateProfile` | `suspend fun updateProfile(email, firstName?, lastName?, phoneNumber?, password): ProfileResponse` | Update profile fields (requires password confirmation) |
| `logout` | `suspend fun logout()` | Clear stored session and tokens |

### CountryRepository

Destination country browsing and search.

| Method | Signature | Description |
|---|---|---|
| `getCountries` | `suspend fun getCountries(): List<Country>` | Fetch all supported destination countries |
| `getCountriesBy` | `suspend fun getCountriesBy(destination: Destination): List<Country>` | Filter countries by code, name, or region |
| `search` | `suspend fun search(query: String): List<Country>` | Search countries by name |
| `getUserLocation` | `suspend fun getUserLocation(): UserLocationResponse` | Detect user's current country via IP |

### PackagesRepository

eSIM data package browsing and stock checks.

| Method | Signature | Description |
|---|---|---|
| `getPackages` | `suspend fun getPackages(destination: Destination): List<PackagePlan>` | Fetch packages for a destination |
| `getTopUpPackages` | `suspend fun getTopUpPackages(iccid: String): List<PackagePlan>` | Fetch top-up packages for an existing eSIM |
| `checkStock` | `suspend fun checkStock(packageTypeId: Int): CheckStockResponse` | Check if a specific package is in stock |
| `getPackageRating` | `suspend fun getPackageRating(): RatingApiResponse` | Fetch app store rating data |

### EsimRepository

eSIM lifecycle management for authenticated users.

| Method | Signature | Description |
|---|---|---|
| `getEsims` | `suspend fun getEsims(): List<AssignedEsim>` | Fetch all eSIMs assigned to the customer |
| `getActiveEsims` | `suspend fun getActiveEsims(): List<AssignedEsim>` | Fetch only non-archived eSIMs |
| `getArchivedEsims` | `suspend fun getArchivedEsims(): List<AssignedEsim>` | Fetch only archived eSIMs |
| `getEsimByIccid` | `suspend fun getEsimByIccid(iccid: String): AssignedEsim` | Fetch a specific eSIM by ICCID |
| `updateEsim` | `suspend fun updateEsim(iccid: String, name: String?, isAutoTopUp: Boolean?, isArchived: Boolean?)` | Update eSIM settings (name, auto top-up, archive) |

### OrdersRepository

Order history and tracking.

| Method | Signature | Description |
|---|---|---|
| `getOrderHistory` | `suspend fun getOrderHistory(): List<OrderHistoryItem>` | Fetch all past orders |
| `getOrderHistory` | `suspend fun getOrderHistory(withLoyaltyPoints: Boolean): List<OrderHistoryItem>` | Fetch orders with optional loyalty points data |
| `getOrderDetails` | `suspend fun getOrderDetails(orderUuid: String): OrderDetail` | Fetch full order details including eSIM profile and QR code |
| `trackOrder` | `suspend fun trackOrder(orderUuid: String)` | Mark an order's conversion as tracked |

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

Kreds loyalty program balance and quotes.

| Method | Signature | Description |
|---|---|---|
| `getLoyaltyBalance` | `suspend fun getLoyaltyBalance(): KredsLoyaltyBalanceResponse` | Fetch the customer's current Kreds balance |
| `getKredsQuote` | `suspend fun getKredsQuote(packageTypeId: Int, loyaltyPointsAmount: Double): KredsQuoteResponse` | Get a discount quote for applying Kreds to a package |

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

Tokens and user data are persisted in `EncryptedSharedPreferences` (AES-256-GCM encryption, AES-256-SIV key encryption). The `DefaultSecureStorage` class handles all read/write operations with automatic fallback to regular `SharedPreferences` if encrypted storage initialization fails.

### Automatic Token Refresh

The `SdkAuthInterceptor` (an OkHttp interceptor) handles token refresh transparently:

1. Every authenticated API request includes a `Bearer` token
2. If the API returns `401 Unauthorized`, the interceptor automatically:
   - Sends a refresh token request to `POST /auth/token/` (grant_type=refresh_token)
   - Updates the stored tokens via `SessionManager.save()`
   - Retries the original request with the new access token
3. If the refresh also fails, the `401` response is returned to the caller

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

Clears all stored tokens and user data from `EncryptedSharedPreferences`, resets `SessionManager` state to `Auth.Unauthenticated`.

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

## Support

For credentials, integration help, or to report a bug, contact:

- **Email:** support@esimplified.io

---

# For Contributors

The remaining sections are intended for SDK maintainers, not integrators.

## Development Workflow

### Making SDK Changes

1. Clone the SDK repository:
   ```bash
   git clone https://github.com/eSimplified/esimplified-android-sdk.git
   ```

2. Make your changes in the SDK source code.

3. Publish to Maven Local for fast local iteration:
   ```bash
   cd esimplified-android-sdk
   ./gradlew publishToMavenLocal
   ```

4. In the app project, Gradle resolves the SDK from Maven Local first (configured via `mavenLocal()` in `settings.gradle.kts`). Sync Gradle and rebuild.

5. Repeat steps 2-4 until satisfied.

### Build Commands

```bash
# Build the SDK AAR
./gradlew :sdk:assembleRelease

# Run tests
./gradlew test

# Publish to Maven Local (for local development)
./gradlew publishToMavenLocal
```

### Output Locations

- AAR: `sdk/build/outputs/aar/sdk-release.aar`
- Maven Local: `~/.m2/repository/io/github/esimplified/android-sdk/{version}/`

## Publishing to Maven Central

The SDK is published to Maven Central via CI/CD. No manual steps needed.

### Automated Publishing (CI/CD)

A GitHub Actions workflow publishes automatically when you push a version tag:

1. Update the version in `sdk/build.gradle.kts`:
   ```kotlin
   mavenPublishing {
       coordinates("io.github.esimplified", "android-sdk", "1.1.0")  // bump version here
   }
   ```

2. Commit and push:
   ```bash
   git add sdk/build.gradle.kts
   git commit -m "chore: bump version to 1.1.0"
   git push origin main
   ```

3. Tag and push:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```

CI will automatically: run tests, sign the artifact with GPG, publish to Maven Central, and create a GitHub Release.

### Manual Publishing (from local machine)

For local publishing (requires Sonatype credentials and GPG key in `~/.gradle/gradle.properties`):

```bash
# Publish to Maven Central
./gradlew publishAllPublicationsToMavenCentralRepository

# Publish to Maven Local (for development)
./gradlew publishToMavenLocal
```

## Versioning

The SDK follows [Semantic Versioning](https://semver.org/):

- **MAJOR** (1.x.x) -- Breaking API changes (removed/renamed repository methods, model field changes that break deserialization)
- **MINOR** (x.1.x) -- New features (new repository methods, new model classes, new optional parameters)
- **PATCH** (x.x.1) -- Bug fixes, internal improvements, documentation updates

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

## Git Workflow

### Branching Model

```
main (production)
  ├── feature/FeatureNameTicketNumber  (e.g., feature/KredsEndpoint1245)
  └── bugfix/BugNameTicketNumber       (e.g., bugfix/QuoteResponseParsing1301)
```

### Flow

1. Create `feature/` or `bugfix/` branch from `main`
2. Work on branch, commit changes
3. PR into `main`
4. After merge, bump version in `sdk/build.gradle.kts`
5. Tag the release: `git tag v1.1.0 && git push origin v1.1.0`
6. CI publishes to Maven Central automatically

### Commit Messages

Use conventional commits: `feat:`, `fix:`, `chore:`, `refactor:`, `test:`, `docs:`, `build:`

---

## License

Proprietary. All rights reserved.
