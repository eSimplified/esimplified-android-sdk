package io.esimplified.sdk.fake

import io.esimplified.sdk.auth.SecureStorageProvider

class FakeSecureStorage : SecureStorageProvider {

    private val secureStore = HashMap<String, String>()
    private val genericStore = HashMap<String, Any?>()

    override fun secureLoad(key: String, default: String): String {
        return secureStore[key] ?: default
    }

    override fun secureSave(value: String, forKey: String) {
        secureStore[forKey] = value
    }

    override fun clearSecureStorage() {
        secureStore.clear()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> load(key: String, default: T): T {
        return (genericStore[key] as? T) ?: default
    }

    override fun <T> save(value: T, forKey: String) {
        genericStore[forKey] = value
    }
}
