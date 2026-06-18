package io.esimplified.sdk.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber

internal class DefaultSecureStorage(context: Context) : SecureStorageProvider {

    private val prefs: SharedPreferences = createEncryptedPrefs(context)

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

    private companion object {
        const val PREFS_NAME = "esimplified_sdk_secure_prefs"

        fun createEncryptedPrefs(context: Context): SharedPreferences {
            return try {
                buildEncryptedPrefs(context)
            } catch (firstAttemptFailure: Exception) {
                Timber.w(
                    firstAttemptFailure,
                    "EncryptedSharedPreferences init failed — wiping prefs file and retrying once"
                )
                context.deleteSharedPreferences(PREFS_NAME)
                try {
                    buildEncryptedPrefs(context)
                } catch (retryFailure: Exception) {
                    Timber.e(
                        retryFailure,
                        "EncryptedSharedPreferences still failing after wipe — refusing to fall back to plaintext"
                    )
                    throw SecureStorageInitException(retryFailure)
                }
            }
        }

        fun buildEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}

/**
 * Thrown when the SDK cannot initialise encrypted storage even after a wipe-and-retry.
 *
 * Consumers must handle this at SDK init by signing the user out and re-prompting for
 * credentials. The SDK will NOT silently fall back to unencrypted storage — auth tokens
 * and other sensitive values would otherwise be written in plaintext on the device.
 */
class SecureStorageInitException(cause: Throwable) : Exception(
    "EncryptedSharedPreferences could not be initialised. " +
        "The SDK refuses to fall back to plaintext storage. " +
        "Sign the user out and prompt them to re-authenticate.",
    cause,
)
