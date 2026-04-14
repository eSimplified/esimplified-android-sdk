# eSIMplified Android SDK

Kotlin SDK for integrating the eSIMplified eSIM platform into Android applications. Provides typed repository interfaces for authentication, eSIM management, package browsing, orders, payments, and more.

## Requirements

- Android `minSdk 28`
- Kotlin 2.x
- [Koin](https://insert-koin.io/) for dependency injection

## Installation

Add the Maven repository and dependency to your project:

```kotlin
// settings.gradle.kts
repositories {
    mavenLocal() // local development
    maven {
        url = uri("https://maven.pkg.github.com/esimplified/esimplified-android-sdk")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("io.esimplified:android-sdk:1.0.0")
}
```

## Quick Start

### 1. Initialize the SDK

Call this once in your `Application.onCreate()`:

```kotlin
EsimSdk.initialize(
    context = this,
    config = SdkConfig(
        baseUrl = "https://api.example.com",
        clientId = "your-client-id",
        clientSecret = "your-client-secret",
        enableLogging = BuildConfig.DEBUG,
    )
)
```

### 2. Load the Koin module

```kotlin
startKoin {
    modules(
        EsimSdk.koinModule(),
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

EsimSdk.initialize(
    context = this,
    config = config,
    storageProvider = MyStorage()
)
```

## Available Repositories

All repositories are provided as Koin singletons. Inject them by interface type.

| Repository | Description |
|---|---|
| `AuthRepository` | Login, registration, password management, profile, logout |
| `CountryRepository` | List supported countries, search, get user location |
| `PackagesRepository` | Browse eSIM packages by destination, top-ups, stock checks |
| `EsimRepository` | List assigned eSIMs, get by ICCID, update settings |
| `OrdersRepository` | Order history, order details, tracking |
| `PaymentsRepository` | Create payment intents |
| `PromoCodeRepository` | Apply, retrieve, and remove promo codes |
| `LoyaltyRepository` | Loyalty balance and Kreds quote |
| `UserRepository` | Local user preference flags |
| `NotificationRepository` | Get and update notification settings |
| `VisaRewardsRepository` | Visa rewards iframe, verification, activation |
| `VouchersRepository` | Redeem voucher codes |

## Authentication

### Login

`AuthRepository.login()` authenticates the user and persists the session automatically:

```kotlin
val authRepo: AuthRepository by inject()

val customer = authRepo.login(email = "user@example.com", password = "password")
```

After a successful login, all subsequent API calls are authenticated via the stored access token. Token refresh is handled by the SDK interceptor.

### Logout

```kotlin
authRepo.logout()
```

Clears the stored session and tokens.

### Observing auth state

The `SessionManager` exposes auth state as a sealed interface:

```kotlin
sealed interface Auth {
    data object Unauthenticated : Auth
    data class Authenticated(user: Customer, ...) : Auth
}
```

Check authentication status via `SessionManager.isAuthenticated()` or inspect `SessionManager.getAuthState()`.

## Example Usage

```kotlin
class ExampleViewModel(
    private val countryRepo: CountryRepository,
    private val packagesRepo: PackagesRepository,
) : ViewModel() {

    private val _packages = MutableStateFlow<List<PackagePlan>>(emptyList())
    val packages: StateFlow<List<PackagePlan>> = _packages.asStateFlow()

    fun loadCountriesAndPackages() = viewModelScope.launch {
        try {
            // Get all supported countries
            val countries = countryRepo.getCountries()

            // Get packages for the first country
            val country = countries.first()
            val plans = packagesRepo.getPackages(
                Destination(code = country.countryCode)
            )

            _packages.value = plans
        } catch (e: Exception) {
            // Handle error
        }
    }
}
```

## License

Proprietary. All rights reserved.
