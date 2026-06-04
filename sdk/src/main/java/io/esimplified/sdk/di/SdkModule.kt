package io.esimplified.sdk.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.esimplified.sdk.EsimplifiedSdk
import io.esimplified.sdk.auth.SecureStorageProvider
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkAuthInterceptor
import io.esimplified.sdk.repository.*
import io.esimplified.sdk.repository.impl.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.module.Module
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.create

internal fun createSdkModule(): Module = module {
    single<SessionManager> { EsimplifiedSdk.sessionManager }
    single<SecureStorageProvider> { EsimplifiedSdk.storageProvider }

    single {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
    }

    single {
        SdkAuthInterceptor(
            sessionManager = get(),
            config = EsimplifiedSdk.config,
        )
    }

    single {
        val builder = OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .addInterceptor(get<SdkAuthInterceptor>())
            .cache(okhttp3.Cache(java.io.File(EsimplifiedSdk.context.cacheDir, "esimplified_http_cache"), 10L * 1024 * 1024))

        if (EsimplifiedSdk.config.enableLogging) {
            builder.addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
        }

        builder.build()
    }

    single {
        val json: Json = get()
        Retrofit.Builder()
            .baseUrl(EsimplifiedSdk.config.baseUrl + "/")
            .client(get())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<ApiService>()
    }

    single<AuthRepository> { AuthRepositoryImpl(get(), get(), get()) }
    single<CountryRepository> { CountryRepositoryImpl(get()) }
    single<PackagesRepository> { PackagesRepositoryImpl(get()) }
    single<EsimRepository> { EsimRepositoryImpl(get()) }
    single<OrdersRepository> { OrdersRepositoryImpl(get()) }
    single<PaymentsRepository> { PaymentsRepositoryImpl(get()) }
    single<PromoCodeRepository> { PromoCodeRepositoryImpl(get(), get()) }
    single<LoyaltyRepository> { LoyaltyRepositoryImpl(get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<NotificationRepository> { NotificationRepositoryImpl(get()) }
    single<VisaRewardsRepository> { VisaRewardsRepositoryImpl(get()) }
    single<VouchersRepository> { VouchersRepositoryImpl(get()) }
}
