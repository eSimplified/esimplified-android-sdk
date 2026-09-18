# eSimplified Android SDK — API Reference

For client teams integrating the SDK into an Android app. Covers installation, configuration, every repository method available to you, and the full shape of every model the API returns.

Documents `io.github.esimplified:android-sdk:3.0.0`, the version on Maven Central, with one
interface change that lands in the next release — `OrdersRepository.getOrderHistory` and
`getOrderHistoryResult` each lost a redundant overload. The tables below show the new shape;
[Changes since 2.0.0](README.md#changes-since-200) has the detail.

For a shorter tour with worked examples, see [README.md](README.md). This document is the complete reference.

Upgrading from 1.x? Start with [Migrating from 1.x to 2.0](README.md#migrating-from-1x-to-20) in the README — money fields changed type, seven unused request types were removed (`EsimRequest`, `EsimPackageListRequest`, `OrderRequest`, `SearchBody`, `CustomerSignIn`, `AuthResponse`, `RewardActivationRequest`), and the compiler does not catch every call site.

---

## Contents

1. [Requirements](#1-requirements)
2. [What you need from eSimplified](#2-what-you-need-from-esimplified)
3. [Installing the SDK](#3-installing-the-sdk)
3b. [Which versions you will receive](#3b-which-versions-you-will-receive)
4. [Configuring and creating the SDK](#4-configuring-and-creating-the-sdk)
5. [Keeping the customer signed in](#5-keeping-the-customer-signed-in)
6. [Making your first call](#6-making-your-first-call)
6b. [The full purchase journey](#6b-the-full-purchase-journey)
6c. [Installing the eSIM](#6c-installing-the-esim)
6d. [Signing out](#6d-signing-out)
7. [Error handling](#7-error-handling)
8. [Caching](#8-caching)
9. [Repository reference](#9-repository-reference)
9b. [Supporting types](#9b-supporting-types)
10. [Model reference](#10-model-reference)
&nbsp;&nbsp;&nbsp;&nbsp;10b. [Customer and profile](#10b-customer-and-profile-models)
&nbsp;&nbsp;&nbsp;&nbsp;10c. [Tokens](#10c-token-models)
&nbsp;&nbsp;&nbsp;&nbsp;10d. [Catalogue and eSIM](#10d-catalogue-and-esim-models)
&nbsp;&nbsp;&nbsp;&nbsp;10e. [Orders and payments](#10e-order-and-payment-models)
&nbsp;&nbsp;&nbsp;&nbsp;10f. [Loyalty and Kreds](#10f-loyalty-and-kreds-models)
&nbsp;&nbsp;&nbsp;&nbsp;10g. [Mokafaa](#10g-mokafaa-models)
&nbsp;&nbsp;&nbsp;&nbsp;10h. [Rewards, vouchers and content](#10h-rewards-voucher-and-content-models)
&nbsp;&nbsp;&nbsp;&nbsp;10i. [Errors](#10i-error-models)

[Support](#support)

---

## 1. Requirements

| | |
|---|---|
| Platform | Android 9 (API 28) or newer — the SDK sets `minSdk 28` |
| `compileSdk` | Anything that supports `minSdk 28`. The AAR sets no floor of its own |
| Kotlin | 2.x |
| Java | 17. The AAR ships class file version 61, so your module needs `compileOptions` and Kotlin `jvmTarget` at 17, built with JDK 17 or newer |
| Dependency injection | **Koin is required in your app.** See [Installing the SDK](#3-installing-the-sdk) |
| Permission | `android.permission.INTERNET`, declared by your app |

The SDK brings Retrofit, OkHttp, kotlinx.serialization, kotlinx.coroutines, `koin-core` and AndroidX Security Crypto transitively at runtime. It declares them `implementation`, so none of them land on your compile classpath and none of them need declaring unless your own code uses them directly.

The SDK registers no components, needs no `Application` subclass of its own, and asks for no runtime permissions. Its manifest contributes one `<meta-data>` entry, `com.google.android.backup.api_key` with the value `unused`; if your app declares that key with a different value, resolve the merge with `tools:replace="android:value"` on your own entry.

## 2. What you need from eSimplified

Before you write any code, ask your eSimplified contact for:

| Value | Used for |
|---|---|
| **Client name** | Identifies your tenant. Becomes the first label of the API host, e.g. `acme` → `https://acme.live.esimplified.io` |
| **Client ID** and **Client secret** | Basic auth for unauthenticated calls, and the OAuth exchange when a customer signs in |
| **AWS WAF token** | Sent as `x-auth-validation`. It defaults to `""`, so the SDK compiles and runs without it, but requests are rejected wherever the WAF is enforcing — ask |
| **Environment** | `STAGING`, `TESTING` or `PRODUCTION` |

Treat the client secret as a secret. Do not ship any of these as string literals — they are trivially extractable from a shipped APK. Fetch them at launch from a server-controlled source, Firebase Remote Config being the usual choice, so credentials can be rotated without an app release.

## 3. Installing the SDK

The SDK is published to Maven Central, which every Gradle project already resolves. No extra repositories and no authentication.

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("io.github.esimplified:android-sdk:3.0.0")
    implementation("io.insert-koin:koin-android:4.1.1")
}

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
```

**The Koin line is not optional.** `EsimplifiedSdk.koinModule()` returns an `org.koin.core.module.Module` and you call Koin's own `startKoin` with it, but the SDK ships `koin-core` at runtime scope only — so neither type is on your compile classpath until you declare Koin yourself. Without that line the integration does not compile. Use `io.insert-koin:koin-androidx-compose` instead if you inject into Compose, and keep Koin on the same 4.x version the SDK uses to avoid a duplicate-class conflict.

Then the permission, which must come from your app because the SDK's manifest declares none:

```xml
<!-- AndroidManifest.xml (app) -->
<uses-permission android:name="android.permission.INTERNET" />
```

Without it Android refuses the socket and every call fails with a `SecurityException`, reaching you as `SdkError.Unknown` on a `…Result` read and thrown on a plain read.

## 3b. Which versions you will receive

Gradle pins you to an exact version. `implementation("io.github.esimplified:android-sdk:3.0.0")` resolves to 3.0.0 and nothing else — there are no version ranges and no BOM in these instructions — so a new release never reaches your build until someone on your team edits that number. Nothing in this section can happen to you without that edit; it describes what you are choosing between when you make it.

| What changed | Version goes | What you do |
|---|---|---|
| A fix | 2.1.0 → 2.1.1 | Nothing |
| Something added | 2.1.0 → 2.2.0 | Nothing |
| Something you call changed or went away | 2.1.0 → 3.0.0 | Update your code, then raise the version you depend on |

Those numbers are illustrative. 2.0.0 is the version on Maven Central and the one this document describes.

The convention follows our commit messages. A `fix:` commit is a patch, a `feat:` commit is a minor, and the major only moves for a change that breaks **callers** — a method you call renamed, removed, or given a new required parameter. Such commits carry a `!`, as in `refactor(orders)!:`.

**The exception is implementing our interfaces.** A minor release can add a method to a repository interface. Your calling code is unaffected, but anyone who writes their own implementation of a repository interface, or a test fake, gains a missing override. [Changes since 2.0.0](README.md#changes-since-200) is an example: collapsing the `getOrderHistory` overloads leaves every call site compiling and asks implementers to delete a now-duplicate override. Changes of that kind are always listed under **Breaking changes** at the top of the release notes, whatever the version number says, so they are never a surprise.

Kotlin gives this SDK an advantage its iOS counterpart does not have. Interface methods can carry default parameter values, so when we add a parameter we give it a default and existing implementations keep working untouched — which is exactly how the optional parameters listed in [New optional parameters on existing methods](README.md#6-new-optional-parameters-on-existing-methods) were added. Swift cannot do that, which is why the iOS SDK meets this problem in a form Android largely avoids.

CI enforces the rule: a version bump that keeps the major while carrying commits marked breaking fails the build.

## 4. Configuring and creating the SDK


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
| sessionManager | SessionManager | No | Custom session handler. Omit it and the SDK uses its own, backed by `storageProvider` |

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

| Parameter | Type | Default | Meaning |
|---|---|---|---|
| `environment` | `SdkEnvironment` | — | `STAGING`, `TESTING` or `PRODUCTION`. Selects the API host |
| `clientName` | `String` | — | Your tenant name, the first label of the API host |
| `apiVersion` | `String` | `"v2"` | API version path segment. Leave as the default unless told otherwise |
| `clientId` | `String` | — | Basic-auth user and OAuth client id |
| `clientSecret` | `String` | — | Basic-auth password and OAuth client secret |
| `awsWafToken` | `String` | `""` | Sent as the `x-auth-validation` header when non-empty |
| `enableLogging` | `Boolean` | `false` | Turns on the SDK's own diagnostics and the OkHttp request/response logger. Debug builds only |
| `customHeadersProvider` | `(() -> Map<String, String>)?` | `null` | Extra headers added to every request. This is where `accept-language` and `accept-currency` belong |
| `enableCaching` | `Boolean` | `true` | Turns the in-memory response cache on or off |
| `defaultCacheTtlSeconds` | `Long` | `3600` | Default cache lifetime in seconds |
| `logger` | `SdkLogger?` | `null` | Receives the SDK's own log lines. See [`SdkLogger`](#sdklogger) |

`SdkConfig`'s primary constructor is `internal`; the constructor above is the public one, and the one to use.

### SdkEnvironment

| Value | Description |
|-------|-------------|
| `STAGING` | `https://{clientName}.stage.esimplified.io` |
| `TESTING` | `https://{clientName}.test.esimplified.io` (short token lifetimes, for auth testing) |
| `PRODUCTION` | `https://{clientName}.live.esimplified.io` |

### Language and currency

The API localises and prices responses from request headers, not from a parameter.

Once a customer is signed in the SDK sends `accept-language` and `accept-currency` for you, taken from `Customer.preferredLanguage` and `Customer.preferredCurrency`. Change them with `AuthRepository.updatePreferences(preferredLanguage, preferredCurrency)`.

Before sign-in, or to override the stored preference, supply them through `customHeadersProvider`: when your provider returns `accept-language` or `accept-currency`, the SDK does not add the stored preference for that header.

```kotlin
SdkConfig(
    // …
    customHeadersProvider = {
        mapOf(
            "accept-language" to Settings.languageCode,
            "accept-currency" to Settings.currencyCode,
        )
    },
)
```

The provider is called on every request, so it picks up the customer's current choice without re-initialising the SDK. `x-auth-validation` returned from it overrides `awsWafToken`; every other key is added as-is. An exception thrown inside the provider is swallowed and the request goes out without the custom headers.

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
classpath until you declare Koin yourself — see
[Installing the SDK](#3-installing-the-sdk).

Repositories are then injected the ordinary way:

```kotlin
class StoreViewModel(
    private val countryRepo: CountryRepository,
) : ViewModel()
```

or resolved directly with `koinInject()` in Compose, or `get()` / `by inject()`
elsewhere.

### EsimplifiedSdk.clearAllCaches()

Drops every cached read. `AuthRepository.logout()` already does this, so call it directly only when you want a clean slate without ending the session.

### SdkLogger

`SdkConfig(logger = …)` takes an implementation of:

```kotlin
fun interface SdkLogger {
    fun log(level: SdkLogLevel, message: String, throwable: Throwable?)
}

enum class SdkLogLevel { DEBUG, WARNING, ERROR }
```

Supply one to route the SDK's own diagnostics into your logging, and it receives every line regardless of build type:

```kotlin
SdkConfig(
    environment = SdkEnvironment.PRODUCTION,
    clientName = "acme",
    clientId = clientId,
    clientSecret = clientSecret,
    logger = { level, message, throwable ->
        when (level) {
            SdkLogLevel.ERROR -> Log.e("MyApp", message, throwable)
            SdkLogLevel.WARNING -> Log.w("MyApp", message, throwable)
            SdkLogLevel.DEBUG -> Log.d("MyApp", message, throwable)
        }
    },
)
```

Leave it null and the SDK writes to `android.util.Log` under the tag `EsimplifiedSdk`, only when the app is debuggable or `enableLogging = true`. The SDK has no logging dependency, and no line carries an email, token, ICCID, customer id, order UUID, voucher code or raw body.

### EsimplifiedSdk.sessionManager

The active `SessionManager`, for reading auth state outside a repository.

---

## 5. Keeping the customer signed in

The SDK persists the session for you. `EsimplifiedSdk.initialize` takes two optional collaborators, and supplying neither is a valid choice:

- **`SecureStorageProvider`** — where tokens are written. Omit it and the SDK uses `EncryptedSharedPreferences` (AES-256-GCM values, AES-256-SIV keys). Supply your own only if you already own a secure store. It never falls back to plaintext: if `EncryptedSharedPreferences` cannot be initialised the default implementation throws `SecureStorageInitException`, and the right response is to sign the customer out and ask them to authenticate again.
- **`SessionManager`** — how auth state is decided and observed. Omit it and the SDK uses its own, which reads and writes through whichever `SecureStorageProvider` is in play. Supply your own when the session already lives in your app.

```kotlin
EsimplifiedSdk.initialize(
    context = this,
    config = config,
    storageProvider = MyKeystoreStorage(this),
    sessionManager = MySessionManager(),
)
```

Full method lists for both are in [Supporting types](#9b-supporting-types).

Token refresh is automatic. An access token within five minutes of expiry is refreshed before the request goes out, and a 401 or 403 on an authenticated request triggers one refresh-and-retry. Concurrent calls share a single refresh rather than racing it. A refresh the server rejects ends the session — `SessionManager.onAuthenticationFailed()` fires and the state becomes `Auth.Unauthenticated` — while a network failure during refresh does not.

Read the current state anywhere with `EsimplifiedSdk.sessionManager`:

```kotlin
when (val state = EsimplifiedSdk.sessionManager.getAuthState()) {
    is Auth.Authenticated -> showAccount(state.user)
    Auth.Unauthenticated -> showSignIn()
}
```

## 6. Making your first call

Repositories are injected by Koin. Every read below works without a signed-in customer except the eSIM list.

```kotlin
class StoreViewModel(
    private val countryRepo: CountryRepository,
    private val packagesRepo: PackagesRepository,
    private val esimRepo: EsimRepository,
    private val authRepo: AuthRepository,
) : ViewModel() {

    fun load(email: String, password: String) = viewModelScope.launch {
        val countries = countryRepo.getCountries()
        val packages = packagesRepo.getPackages(Destination(code = "ZA", slug = "south-africa"))

        authRepo.login(email = email, password = password)
        val esims = esimRepo.getActiveEsims()
    }
}
```

## 6b. The full purchase journey

The SDK gets you an order. It does **not** take the payment and it does **not** install the eSIM — both of those happen in your app. This is the whole journey, with the handoffs marked.

```kotlin
suspend fun buy(
    countryRepo: CountryRepository,
    packagesRepo: PackagesRepository,
    authRepo: AuthRepository,
    paymentsRepo: PaymentsRepository,
    ordersRepo: OrdersRepository,
    email: String,
    password: String,
) {
    // 1. Browse, no sign-in needed
    val countries = countryRepo.getCountries()
    val packages = packagesRepo.getPackages(Destination(code = "ZA", slug = "south-africa"))
    val plan = packages.first()

    // 2. The customer must be signed in to buy
    val customer = authRepo.login(email = email, password = password)

    // 3. Ask the API to create a payment
    val payment = paymentsRepo.getPaymentIntent(
        PaymentRequest(
            type = PaymentRequest.Type.BUY,          // TOP_UP to top an existing eSIM up
            iccid = null,                            // the eSIM's ICCID when topping up
            customer = customer.details(),
            packageTypeId = plan.packageTypeId.toInt(),
            paymentMethod = PaymentRequest.Method.STRIPE_INTENT,
            autoTopUp = false,
            savePaymentMethod = true,
        )
    )
    val transaction = payment.transaction ?: return

    // 4. YOUR APP takes the payment — the SDK stops here
    //    transaction.zeroCharge == true → nothing to pay, skip straight to step 5
    //    otherwise hand these to the Stripe Android SDK:
    //      transaction.publishableKey, transaction.uri (the client secret),
    //      transaction.ephemeralKey, transaction.customerRef

    // 5. Once Stripe reports success, read the order
    val orderUuid = transaction.orderId ?: return
    val order = ordersRepo.getOrderDetails(orderUuid = orderUuid, forceRefresh = true)

    // 6. YOUR APP installs the eSIM — see "Installing the eSIM" below
    //    order.smDpAddress, order.activationCode

    // 7. Tell the API the conversion is recorded, so it is not counted twice
    ordersRepo.trackOrder(orderUuid = orderUuid)
}
```

`customer.details()` is an extension on `Customer` declared in its companion, so it needs one import:

```kotlin
import io.esimplified.sdk.model.Customer.Companion.details
```

Build a `CustomerDetails` by hand instead if you are buying for a customer you did not just fetch.

### An order is not ready the instant it is paid

Provisioning is asynchronous. Immediately after payment the order comes back with `orderStatus` `"pending"` and **no** `qrCode`, `smDpAddress`, `activationCode` or `esimProfile` — every one of those is nullable for exactly this reason. Poll `getOrderDetails(orderUuid, forceRefresh = true)` until `smDpAddress` and `activationCode` are both present, and give the wait a deadline:

```kotlin
suspend fun awaitProvisionedOrder(
    ordersRepo: OrdersRepository,
    orderUuid: String,
    attempts: Int = 10,
): OrderDetail? {
    repeat(attempts) {
        val order = ordersRepo.getOrderDetails(orderUuid = orderUuid, forceRefresh = true)
        if (!order.smDpAddress.isNullOrBlank() && !order.activationCode.isNullOrBlank()) return order
        delay(3_000)
    }
    return null
}
```

If the deadline expires, tell the customer the order has not completed rather than sending them into an install that cannot succeed. Their payment is safe and the order completes server-side.

## 6c. Installing the eSIM

The SDK hands you the credentials; Android does the install. There is no SDK call for this — you launch the platform's LPA (Local Profile Assistant) yourself.

**1. A compatibility check**, so you do not offer installation on a device that cannot do it:

```kotlin
fun isDeviceEsimCapable(context: Context): Boolean {
    val hasEuiccFeature = context.packageManager
        .hasSystemFeature(PackageManager.FEATURE_TELEPHONY_EUICC)
    val euiccManager = context.getSystemService(EuiccManager::class.java)
    return hasEuiccFeature || euiccManager?.isEnabled == true
}
```

**2. The install itself.** Android exposes eSIM provisioning through a universal link that the system LPA handles. Build the activation string in LPA format from the order's credentials and start it as an `ACTION_VIEW` intent — the system takes over from there and shows its own UI:

```kotlin
fun installEsim(context: Context, smDpAddress: String, activationCode: String): Boolean {
    val cardData = "LPA:1\$$smDpAddress\$$activationCode"
    val uri = Uri.Builder()
        .scheme("https")
        .authority("esimsetup.android.com")
        .path("/esim_qrcode_provisioning")
        .appendQueryParameter("carddata", cardData)
        .build()
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: Exception) {
        false
    }
}
```

The `carddata` value is exactly what a printed eSIM QR code encodes: the literal `LPA:1`, the SM-DP+ address, and the matching ID, separated by `$`. In Kotlin that first `$` has to be escaped, which is why the string reads `"LPA:1\$$smDpAddress\$$activationCode"`.

Catch the failure rather than assuming it works. `startActivity` throws `ActivityNotFoundException` on a device with no LPA, and some OEM builds refuse the link even when `EuiccManager` reports eSIM support.

**3. Always offer a manual fallback.** Render `order.qrCode` as a QR image — or show `order.qrCodeImageBase64`, which the API returns as a base64-encoded image — for scanning on another device, and show `order.smDpAddress` and `order.activationCode` as text so the customer can type them into Settings. Some customers install on a second phone, and some devices refuse the direct install.

`EsimRepository.getEsimByIccid` exposes the same credentials for an eSIM the customer already owns, so a re-install does not need the original order.

## 6d. Signing out

```kotlin
authRepo.logout()
```

`logout()` saves `Auth.Unauthenticated` and clears every cached response in one step, so there is nothing else to call. That second half matters: cached reads are keyed by endpoint, not by customer, so a sign-out that left them in place would leave one customer's eSIMs and orders readable by the next person to sign in on that device.

`EsimplifiedSdk.clearAllCaches()` does the cache half on its own, for when you want a clean slate without ending the session.

---

## 7. Error handling

Every cached read comes in two forms, and the choice is about how loudly a failure should land:

| Variant | Behaviour |
|---|---|
| `getX(...)` | Returns the value. A cache miss plus a failed refresh **throws**; a stale cache entry is returned rather than thrown |
| `getXResult(...)` | Returns `RepositoryResult<T>` — the value, whether it came from a stale cache, and the `SdkError` that caused that. Never throws for a network failure. Lets you show data and an error together |

```kotlin
val result = esimRepo.getEsimsResult()
render(result.value)
if (result.isStale && result.isOffline) showOfflineBanner()
```

`SdkError`, in `io.esimplified.sdk.network`, is a sealed subclass of `IOException`. These six cases are the whole hierarchy:

| Case | Constructor | Meaning |
|---|---|---|
| `NetworkError` | `NetworkError(statusCode: Int, message: String)` | Server responded with an error status |
| `AuthenticationRequired` | `AuthenticationRequired()` | No valid session |
| `NoInternetConnection` | `NoInternetConnection()` | Host unreachable or connection refused |
| `DecodingError` | `DecodingError(cause: Throwable)` | Response did not match the model. `cause` names the field that broke |
| `InvalidURL` | `InvalidURL(url: String)` | Malformed URL |
| `Unknown` | `Unknown(cause: Throwable)` | Anything else |

`SdkError.isOffline` is shorthand for `this is NoInternetConnection`.

The SDK's other public exception types, all thrown rather than returned:

| Exception | Package | Thrown by | Meaning |
|---|---|---|---|
| `InvalidRefreshTokenException()` | `io.esimplified.sdk.repository` | `AuthRepository.loginWithRefreshToken` | The stored refresh token was rejected. The session has already been cleared — send the customer to sign-in. Its message is `"Session expired. Please sign in again."` |
| `LoyaltyApiException(httpCode: Int, message: String?)` | `io.esimplified.sdk.network` | `LoyaltyRepository`'s Mokafaa methods | Backend error, `message` verbatim. Branch on `httpCode` (400 / 401 / 503) |
| `PaymentApiException(httpCode: Int, type: String?, message: String?)` | `io.esimplified.sdk.network` | `PaymentsRepository.getPaymentIntent` | Payment rejected. `type` is `PaymentApiException.TYPE_VALIDATION_ERROR` for a bad request |
| `SecureStorageInitException(cause: Throwable)` | `io.esimplified.sdk.auth` | `EsimplifiedSdk.initialize()`, via the default storage provider | `EncryptedSharedPreferences` could not be initialised. The SDK refuses to fall back to plaintext — sign the customer out and re-prompt |

`VouchersRepository.redeemVoucher` is the one method that returns Kotlin's own `Result<T>` instead. Other API failures surface as a plain `Exception` whose message is the backend's error text, parsed out of the response body.

---

## 8. Caching

Every list and detail read is served through an in-process cache keyed by call and arguments. Two optional arguments appear on those methods, omitted from the tables in [Repository reference](#9-repository-reference) for brevity:

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `forceRefresh: Boolean` | `false` (`true` on `getLoyaltyBalance` and `getLoyaltyBalanceResult`) | Skip the cache and go to the network |
| `cacheTTL: Duration` | the repository's own constant, e.g. `EsimRepository.ESIM_LIST_TTL` | How long this read stays fresh |

Cache control:

- `EsimplifiedSdk.clearAllCaches()` — drop everything. `AuthRepository.logout()` already does this for you.
- `repository.invalidateCache()` — `suspend`, on every cached repository; drops just that repository's entries.
- `SdkConfig(enableCaching = false)` — gives every entry a zero TTL, so every read goes to the network. Per-call `cacheTTL` arguments still apply when caching is on.
- `SdkConfig(defaultCacheTtlSeconds = …)` — the fallback lifetime, 3600 seconds by default, used where a call passes no TTL of its own.

The cache lives in memory for the life of the process. Nothing is written to disk, so a cold start always goes to the network.

### Every `…Result` twin

One row per `…Result` method in the SDK. They take the same arguments as the method they wrap. Five of them return a **nullable** inner type where the plain method returns non-null — that is the case where nothing was ever cached and the refresh failed, so read those rows carefully.

| Repository | Result method | Plain method | Returns |
|---|---|---|---|
| `CountryRepository` | `getCountriesResult` | `getCountries` | `RepositoryResult<List<Country>>` |
| `CountryRepository` | `getCountriesByResult` | `getCountriesBy` | `RepositoryResult<List<Country>>` |
| `EsimRepository` | `getEsimsResult` | `getEsims` | `RepositoryResult<List<AssignedEsim>>` |
| `EsimRepository` | `getActiveEsimsResult` | `getActiveEsims` | `RepositoryResult<List<AssignedEsim>>` |
| `EsimRepository` | `getArchivedEsimsResult` | `getArchivedEsims` | `RepositoryResult<List<AssignedEsim>>` |
| `EsimRepository` | `getEsimByIccidResult` | `getEsimByIccid` | `RepositoryResult<AssignedEsim?>` |
| `FaqAndSupportRepository` | `fetchDestinationFaqsResult` | `fetchDestinationFaqs` | `RepositoryResult<List<Faq>>` |
| `LoyaltyRepository` | `getLoyaltyBalanceResult` | `getLoyaltyBalance` | `RepositoryResult<KredsLoyaltyBalanceResponse?>` |
| `OrdersRepository` | `getOrderHistoryResult` | `getOrderHistory` | `RepositoryResult<List<OrderHistoryItem>>` |
| `OrdersRepository` | `getOrderDetailsResult` | `getOrderDetails` | `RepositoryResult<OrderDetail?>` |
| `OrdersRepository` | `getOrdersPageResult` | — none; this one is `Result`-only | `RepositoryResult<OrdersPage>` |
| `PackagesRepository` | `getPackagesResult` | `getPackages` | `RepositoryResult<List<PackagePlan>>` |
| `PackagesRepository` | `getTopUpPackagesResult` | `getTopUpPackages` | `RepositoryResult<List<PackagePlan>>` |
| `PackagesRepository` | `getPackagesPageResult` | `getPackagesPage` | `RepositoryResult<PackagesPage>` |
| `PackagesRepository` | `checkStockResult` | `checkStock` | `RepositoryResult<CheckStockResponse?>` |
| `StoreReviewRepository` | `fetchStoreReviewResult` | `fetchStoreReview` | `RepositoryResult<RatingApiResponse?>` |
| `ThemeRepository` | `fetchPageThemeResult` | `fetchPageTheme` | `RepositoryResult<ThemePage?>` |
| `ThemeRepository` | `fetchDestinationThemeResult` | `fetchDestinationTheme` | `RepositoryResult<ThemeDestination?>` |

`RepositoryResult` itself is in [Supporting types](#repositoryresult).

---

## 9. Repository reference

One row per method, and every method appears exactly once — count the rows and
you have counted the API. The `Parameters` column carries each parameter's name,
type and default. The two arguments every cached read also accepts,
`forceRefresh` and `cacheTTL`, are left out of these tables and described once
under [Caching](#8-caching); so are the `…Result` twins, which take the same
arguments as the method they wrap and are listed in full
[there](#every-result-twin).

All repository functions are `suspend` unless noted. Inject via Koin, which
your app must declare as a dependency — see
[`EsimplifiedSdk.koinModule()`](#esimplifiedsdkkoinmodule):

```kotlin
class AccountViewModel(
    private val authRepo: AuthRepository,
) : ViewModel()
```

Outside a constructor, use Koin's own resolvers: `get<AuthRepository>()` inside a
Koin component or module, `by inject<AuthRepository>()` in an `Activity` or
`Fragment` that implements `KoinComponent`, or `koinInject<AuthRepository>()`
inside a `@Composable` (that one needs `koin-androidx-compose`).

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
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

Cached: `getCountries`, `getCountriesBy` (`COUNTRIES_TTL` = 24 h). `…Result` twins: `getCountriesResult`, `getCountriesByResult`.

---

### PackagesRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getPackages` | `destination: Destination` | `List<PackagePlan>` | Get eSIM packages for a destination |
| `getPackagesPage` | `destination: Destination` | `PackagesPage` | Same read plus total count and the page's promo code |
| `getTopUpPackages` | `iccid: String` | `List<PackagePlan>` | Get top-up packages for an existing eSIM |
| `checkStock` | `packageTypeId: Int` | `CheckStockResponse` | Check package availability |
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

All cached (`PACKAGES_TTL` = 1 h). `…Result` twins: `getPackagesResult`, `getPackagesPageResult`, `getTopUpPackagesResult`, `checkStockResult`.

`getPackageRating()` moved to `StoreReviewRepository.fetchStoreReview()` in 2.0.

---

### EsimRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getEsims` | `showLegacy: Boolean? = null, isPrimary: Boolean? = null, includeBase64QrCode: Boolean = false` | `List<AssignedEsim>` | Get all user's eSIMs. See **`showLegacy` has three cases** below |
| `getActiveEsims` | `showLegacy: Boolean? = null, isPrimary: Boolean? = null, includeBase64QrCode: Boolean = false` | `List<AssignedEsim>` | Non-archived eSIMs only. See **`showLegacy` has three cases** below |
| `getArchivedEsims` | `showLegacy: Boolean? = null, isPrimary: Boolean? = null, includeBase64QrCode: Boolean = false` | `List<AssignedEsim>` | Archived eSIMs only. See **`showLegacy` has three cases** below |
| `getEsimByIccid` | `iccid: String, includeBase64QrCode: Boolean = false` | `AssignedEsim` | Get one eSIM from `customer/esims/{iccid}/details/` |
| `updateEsim` | `iccid: String, name: String? = null, isAutoTopUp: Boolean? = null, isArchived: Boolean? = null, isPrimary: Boolean? = null` | `Unit` | Update eSIM settings. **Throws if the server rejects the write** — on a non-2xx response, or when the success body is not the API's `eSIM updated successfully`, matching the iOS SDK |
| `updateEsimPrimaryStatus` | `iccid: String, isPrimary: Boolean` | `Unit` | Convenience wrapper for the primary flag |
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

Cached: `ESIM_LIST_TTL` = 24 h for lists, `ESIM_DETAILS_TTL` = 5 min for details. `…Result` twins: `getEsimsResult`, `getActiveEsimsResult`, `getArchivedEsimsResult`, `getEsimByIccidResult`.

**`showLegacy` has three cases, not two.** `null` is not the same as `false`, which is why the parameter is `Boolean?` on every list read:

| You pass | The request carries | The API returns |
|---|---|---|
| left out, or `null` | no `show_legacy` parameter at all | all **universal** eSIMs, for tenants that `use_universal` |
| set to `false` | `show_legacy=false` | just universal eSIMs |
| set to `true` | `show_legacy=true` | **all** eSIMs, universal and legacy |

Leaving the parameter out lets the API decide by tenant; `false` states the choice; `true` widens the list. The three are cached separately, so a list fetched under one never satisfies a read asking for another.

`includeBase64QrCode = true` asks the API to embed the QR image, populating `AssignedEsim.qrCodeImageBase64` alongside `smDpAddress` and `activationCode`. `AssignedEsim.canInstallDirectly` reports whether those are enough to install without an order lookup. Lists are requested newest-first (`order_by=-assigned_date`), capped at 1000.

---

### OrdersRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getOrderHistory` | `withLoyaltyPoints: Boolean = false` | `List<OrderHistoryItem>` | Past orders. `withLoyaltyPoints = true` asks the API for the points earned and spent on each |
| `getOrderDetails` | `orderUuid: String` | `OrderDetail` | Get detailed order info |
| `getOrdersPageResult` | `limit: Int = 100, offset: Int = 0, withLoyaltyPoints: Boolean = false` | `RepositoryResult<OrdersPage>` | Paged order read |
| `getOrderInvoice` | `orderUuid: String` | `ByteArray` | Download the order's PDF invoice bytes |
| `trackOrder` | `orderUuid: String` | `Unit` | Mark the order's conversion as tracked (never throws) |
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

Cached: `ORDERS_LIST_TTL` = 10 min, `ORDER_DETAIL_TTL` = 5 min. `…Result` twins: `getOrderHistoryResult`, `getOrderDetailsResult`.

`getOrderDetails` retries an order still in `pending` status up to 5 times, one second apart, before returning it.

---

### PaymentsRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `getPaymentIntent` | `request: PaymentRequest` | `PaymentResponse` | Create Stripe payment intent |

**PaymentRequest:**

```kotlin
PaymentRequest(
    type: String,                        // PaymentRequest.Type.BUY or .TOP_UP
    iccid: String? = null,               // required for a top-up
    customer: CustomerDetails,
    packageTypeId: Int,
    paymentMethod: String,               // PaymentRequest.Method.STRIPE_INTENT or .STRIPE_CHECKOUT
    autoTopUp: Boolean,
    savePaymentMethod: Boolean,
    loyaltyPointsAmount: Double? = null,
    loyaltyProvider: String? = null,     // LoyaltyProvider.KREDS or .MOKAFAA
    loyaltyPointsToUse: Int? = null,
    couponId: String? = null,
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
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

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
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

Cached (`THEME_TTL` = 1 h). `…Result` twins: `fetchPageThemeResult`, `fetchDestinationThemeResult`.

---

### FaqAndSupportRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `fetchDestinationFaqs` | `countryNameSlug: String` | `List<Faq>` | FAQs for a destination slug |
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

Cached (`FAQS_TTL` = 24 h). `…Result` twin: `fetchDestinationFaqsResult`.

---

### StoreReviewRepository

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `fetchStoreReview` | — | `RatingApiResponse` | Store review summary, reviews and stats |
| `invalidateCache` | — | `Unit` | Drop this repository's cached entries |

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

## 9b. Supporting types

Types you meet in signatures that are not models. Each is defined once; the first table says where the rest live.

### Defined elsewhere in this document

| Type | Defined in |
|---|---|
| `SdkEnvironment` | [Configuring and creating the SDK](#sdkenvironment) |
| `SdkLogger`, `SdkLogLevel` | [Configuring and creating the SDK](#sdklogger) |
| `SdkError` and its six cases | [Error handling](#7-error-handling) |
| `InvalidRefreshTokenException`, `LoyaltyApiException`, `PaymentApiException`, `SecureStorageInitException` | [Error handling](#7-error-handling) |
| `OrdersPage` | [Order and payment models](#orderspage) |
| `PackagesPage` | [Catalogue and eSIM models](#packagespage) |

### RepositoryResult

What every `…Result` method returns.

```kotlin
data class RepositoryResult<T>(
    val value: T,
    val isStale: Boolean = false,
    val failure: SdkError? = null,
) {
    val didFail: Boolean
    val isOffline: Boolean
}
```

| Property | Type | Meaning |
|---|---|---|
| `value` | `T` | The data. Present even when the call failed, if a stale cache could serve it |
| `isStale` | `Boolean` | `true` when `value` came from an expired cache rather than the network |
| `failure` | `SdkError?` | Why the network call failed, or `null` if it did not |
| `didFail` | `Boolean` | `failure != null` |
| `isOffline` | `Boolean` | `failure` is `SdkError.NoInternetConnection` |

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

### Auth

Sealed interface describing the session.

```kotlin
Auth.Unauthenticated          // No active session

Auth.Authenticated(
    user: Customer,            // User profile
    expires: LocalDateTime,    // Token expiry
    accessToken: String,       // OAuth access token
    refreshToken: String       // OAuth refresh token
)
```

`Auth.Authenticated.isExpired` is `true` once the token is within five minutes of `expires`, which is the window the SDK refreshes in.

### TokenProvider

```kotlin
interface TokenProvider {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun saveTokens(access: String, refresh: String)
    suspend fun refreshAccessToken(): Boolean
}
```

Public but unused: nothing in the SDK consumes a `TokenProvider`, and supplying one has no effect. Token storage is `SecureStorageProvider` and session state is `SessionManager`. Listed here only because it is visible on the public surface.

---

## 10. Model reference

Every type the SDK returns or accepts, with its Kotlin properties and the JSON keys they map to. A `?` means the field can be absent or null.

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
| mokafaaEnabled | Boolean? | Whether Mokafaa is available to this customer (`mokafaa_enabled`) |
| mokafaaCicNo | String? | The customer's Mokafaa identifier (`mokafaa_cic_no`) |
| externalReference | String? | Your own reference for this customer, if the tenant sets one (`external_reference`) |
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
| currency | String? | Currency code |
| currencyObject | CurrencyObject? | Currency details |
| flagCss | String | CSS class for a sprite-sheet flag, for web reuse (`flag_css`) |
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
| fromPriceValue | Double? | `fromPrice` parsed, for arithmetic |
| isGlobal | Boolean | True when `code` is the global destination code `2A` |

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
| network | List\<String\>? | Carrier networks the plan roams on |
| packageSlug | String | URL slug |
| validityDays | Long | Validity in days |
| packageTypeId | Long | Unique package ID |
| supportedCountries | List\<SupportedCountry\> | Countries covered |
| discountedPrice | String? | Discounted price, server text verbatim |
| earnPercentage | Double? | Kreds earn percentage |
| dataCap | String? | Fair usage data cap |
| throttleSpeed | String? | Throttled speed after cap |
| kycDisplay | String | KYC requirement text to show before purchase (`kyc_display`) |
| bestConnectivity | String | Network the API recommends for this destination (`best_connectivity`) |
| activationPolicy | String | When validity starts, e.g. on first connection (`activation_policy`) |
| nameAdditionalText | String | Qualifier shown after the package name (`name_additional_text`) |
| validityDaysDisplay | String | Validity already formatted for display (`validity_days_display`) |
| discountLabel | String | Badge text for the discount, when there is one (`discount_label`) |
| discountPercentage | String? | Discount percentage as server text (`discount_percentage`) |
| promoCode | CheckoutCouponResponse? | The promo code already applied to this price, if any (`promo_code`) |
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
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
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
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
| customerId | String | Customer the order belongs to (`customer_id`) |
| countryCode | String? | Destination country code (`country_code`) |
| countryName | String? | Destination country name (`country_name`) |
| smDpAddress | String? | SM-DP+ server address — half of what the eSIM install needs (`sm_dp_address`) |
| esimProfile | EsimProfile? | The provisioned profile, once the order completes (`profile`) |
| packageInfo | PackagePlan? | The package that was bought (`package`) |
| transactionId | String? | Payment gateway transaction reference (`transaction_id`) |
| tracked | Boolean | Whether the conversion has already been reported by `trackOrder` (`conversion_tracked`) |
| detail | String? | Message returned instead of an order when the lookup failed |
| passwordResetEncoded | String? | Token for the set-a-password flow after a guest purchase (`password_reset_encoded`) |
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
| priceValue | Double | `price` parsed |
| finalPriceValue | Double | `finalPrice` parsed |
| discountAmountValue | Double | `discountAmount` parsed |

### PaymentResponse

| Field | Type | Description |
|-------|------|-------------|
| detail | String? | Error detail |
| transaction | Transaction? | Transaction info |
| type | String? | Error classification when the request was rejected |
| message | String? | Human-readable error message when the request was rejected |

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
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
| isRemovable | Boolean | Whether the customer may clear this code — valid, a non-zero percentage, and not a Visa reward |

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
| eid | String? | eUICC identifier of the device the profile is on |
| imsi | Long? | IMSI assigned to the profile |
| iccid | String? | ICCID |
| state | EsimProfileState? | ENABLED, DOWNLOADED, INSTALLED, DISABLED, DELETED, RELEASED, ERROR |
| status | String? | State as server text, more detailed than `state` (`state_message`) |
| activationCode | String? | Activation code |
| ccRequired | Boolean? | Whether a confirmation code is needed to install (`cc_required`) |
| releaseDate | Long? | Release date as an epoch value (`release_date`) |
| releaseDateUtc | String? | The same release date as a UTC string (`release_date_utc`) |
| lastOperationDate | Long? | Last profile operation as an epoch value (`last_operation_date`) |
| lastOperationDateUtc | String? | The same date as a UTC string (`last_operation_date_utc`) |
| reuseEnabled | Boolean? | Whether the profile may be installed again (`reuse_enabled`) |
| reuseRemainingCount | Int? | Installs still allowed (`reuse_remaining_count`) |
| policy | ProfileReusePolicy? | Reuse type and maximum count (`profile_reuse_policy`) |
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
| installed | Boolean | True when `state` is ENABLED, DOWNLOADED, INSTALLED or DISABLED |
| isDeleted | Boolean | True when `state` is DELETED |

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
| details | String? | Longer detail message; separate from `detail` on the wire (`details`) |
| redirectURl | String? | Where to send the customer to claim the reward (`redirect_url`) |
| validityDays | Int? | Reward validity |
| dataGB | Double? | Data reward amount |
| **Computed properties** | — | Derived in Kotlin from the fields above; not part of the JSON |
| remainingOrAllowed | Int? | `remaining ?: allowed` |

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
| sku | String? | Package SKU the review is about |

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

## 10b. Customer and profile models

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

## 10c. Token models

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

## 10d. Catalogue and eSIM models

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

## 10e. Order and payment models

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

## 10f. Loyalty and Kreds models

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

## 10g. Mokafaa models

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

## 10h. Rewards, voucher and content models

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

## 10i. Error models

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

---

## Support

Questions, credentials and environment access: your eSimplified contact. Bugs in the SDK itself: open an issue on the [repository](https://github.com/eSimplified/esimplified-android-sdk/issues), and include the `SdkError` subclass and message you hit — for a `DecodingError` the `cause` names the field that broke.
