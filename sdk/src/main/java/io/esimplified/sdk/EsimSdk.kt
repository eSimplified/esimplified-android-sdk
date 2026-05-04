package io.esimplified.sdk

import android.content.Context
import io.esimplified.sdk.auth.DefaultSecureStorage
import io.esimplified.sdk.auth.DefaultSessionManager
import io.esimplified.sdk.auth.SecureStorageProvider
import io.esimplified.sdk.auth.SessionManager
import io.esimplified.sdk.di.createSdkModule
import org.koin.core.module.Module

object EsimplifiedSdk {
    private var _config: SdkConfig? = null
    private var _sessionManager: SessionManager? = null
    private var _storageProvider: SecureStorageProvider? = null
    private var _context: Context? = null

    internal val config: SdkConfig
        get() = _config ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    val sessionManager: SessionManager
        get() = _sessionManager ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    internal val storageProvider: SecureStorageProvider
        get() = _storageProvider ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

    internal val context: Context
        get() = _context ?: error("EsimplifiedSdk not initialized. Call EsimplifiedSdk.initialize() first.")

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
    }

    fun koinModule(): Module = createSdkModule()
}
