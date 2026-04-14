package io.esimplified.sdk.auth

interface SecureStorageProvider {
    fun secureLoad(key: String, default: String): String
    fun secureSave(value: String, forKey: String)
    fun clearSecureStorage()
    fun <T> load(key: String, default: T): T
    fun <T> save(value: T, forKey: String)
}
