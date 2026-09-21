package io.esimplified.sdk.auth

internal sealed interface StoredValue {
    data class Present(val value: String) : StoredValue
    data object Absent : StoredValue
    data object Unreadable : StoredValue
}

internal interface DurableSecureStorage {
    fun secureRead(key: String): StoredValue
    fun secureSaveDurably(value: String, forKey: String): Boolean
}
