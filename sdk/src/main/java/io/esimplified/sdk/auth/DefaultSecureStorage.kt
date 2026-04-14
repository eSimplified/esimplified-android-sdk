package io.esimplified.sdk.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

internal class DefaultSecureStorage(context: Context) : SecureStorageProvider {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "esimplified_sdk_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Timber.e(e, "Failed to create EncryptedSharedPreferences, falling back to regular prefs")
        context.getSharedPreferences("esimplified_sdk_secure_prefs", Context.MODE_PRIVATE)
    }

    override fun secureLoad(key: String, default: String): String {
        return try {
            prefs.getString(key, default) ?: default
        } catch (e: Exception) {
            Timber.e(e, "Failed to secureLoad key=$key")
            default
        }
    }

    override fun secureSave(value: String, forKey: String) {
        try {
            prefs.edit().putString(forKey, value).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to secureSave key=$forKey")
        }
    }

    override fun clearSecureStorage() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clearSecureStorage")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> load(key: String, default: T): T {
        return try {
            when (default) {
                is String -> (prefs.getString(key, default) ?: default) as T
                is Boolean -> prefs.getBoolean(key, default) as T
                is Int -> prefs.getInt(key, default) as T
                is Long -> prefs.getLong(key, default) as T
                is Float -> prefs.getFloat(key, default) as T
                else -> default
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load key=$key")
            default
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> save(value: T, forKey: String) {
        try {
            val editor = prefs.edit()
            when (value) {
                is String -> editor.putString(forKey, value)
                is Boolean -> editor.putBoolean(forKey, value)
                is Int -> editor.putInt(forKey, value)
                is Long -> editor.putLong(forKey, value)
                is Float -> editor.putFloat(forKey, value)
                else -> {
                    Timber.w("Unsupported type for save: ${value?.let { it::class.simpleName }}")
                    return
                }
            }
            editor.apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save key=$forKey")
        }
    }
}
