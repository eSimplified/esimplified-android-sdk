package io.esimplified.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import io.esimplified.sdk.auth.DefaultSecureStorage
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.auth.SecureStorageProvider
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.di.createSdkModule
import io.esimplified.sdk.network.SdkCache
import org.koin.core.module.Module
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object EsimplifiedSdk {
    private var _config: SdkConfig? = null
    private var _sessionManager: SessionManager? = null
    private var _storageProvider: SecureStorageProvider? = null
    private var _context: Context? = null
    private var _cache: SdkCache? = null

    internal val config: SdkConfig
        get() = _config ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    val sessionManager: SessionManager
        get() = _sessionManager ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    internal val storageProvider: SecureStorageProvider
        get() = _storageProvider ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    internal val context: Context
        get() = _context ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    internal val cache: SdkCache
        get() = _cache ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    fun initialize(
        context: Context,
        config: SdkConfig,
        storageProvider: SecureStorageProvider? = null,
        sessionManager: SessionManager? = null
    ) {
        _context = context.applicationContext
        _config = config
        _storageProvider = storageProvider ?: DefaultSecureStorage(context.applicationContext)
        _sessionManager = sessionManager ?: DefaultSessionManager(_storageProvider!!)
        SdkLog.delegate = config.logger
        SdkLog.isEnabled = config.enableLogging || context.applicationContext.isDebuggable()
        _cache = SdkCache(
            if (config.enableCaching) config.defaultCacheTtlSeconds.seconds else Duration.ZERO
        )
    }

    fun clearAllCaches() {
        val cache = _cache
        if (cache == null) {
            SdkLog.d("clearAllCaches called before initialize; nothing to clear")
            return
        }
        cache.clear()
        SdkLog.d("Cleared all SDK caches")
    }

    fun koinModule(): Module = createSdkModule()

    private fun Context.isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
