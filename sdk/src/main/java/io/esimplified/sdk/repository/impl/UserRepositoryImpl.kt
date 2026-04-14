package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.UserRepository

import io.esimplified.sdk.auth.SecureStorageProvider


internal class UserRepositoryImpl(
    private val storage: SecureStorageProvider
) : UserRepository {

    companion object {
        private const val ESIM_SUPPORTED_POPUP = "ESIM_SUPPORTED_POPUP"
        private const val ESIM_NOT_SUPPORTED_POPUP = "ESIM_NOT_SUPPORTED_POPUP"
    }

    override fun saveEsimSupportedPopupFlag(value: Boolean) {
        storage.save(value = value, forKey = ESIM_SUPPORTED_POPUP)
    }

    override fun getEsimSupportedPopupFlag(): Boolean {
        return storage.load(ESIM_SUPPORTED_POPUP, false)
    }

    override fun saveEsimNotSupporterPopupFlag(value: Boolean) {
        storage.save(value, forKey = ESIM_NOT_SUPPORTED_POPUP)
    }

    override fun getEsimNotSupportedPopupFlag(): Boolean {
        return storage.load(ESIM_NOT_SUPPORTED_POPUP, false)
    }
}
