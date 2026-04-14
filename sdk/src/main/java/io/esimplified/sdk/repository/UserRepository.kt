package io.esimplified.sdk.repository

interface UserRepository {
    //Need to come up with better naming
    fun saveEsimSupportedPopupFlag(value: Boolean)
    fun getEsimSupportedPopupFlag(): Boolean
    fun saveEsimNotSupporterPopupFlag(value: Boolean)
    fun getEsimNotSupportedPopupFlag(): Boolean
}
