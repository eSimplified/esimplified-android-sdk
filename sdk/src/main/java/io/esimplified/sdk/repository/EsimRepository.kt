package io.esimplified.sdk.repository

import io.esimplified.sdk.model.AssignedEsim

interface EsimRepository {
    suspend fun getEsims(): List<AssignedEsim>
    suspend fun getEsimByIccid(iccid: String): AssignedEsim
    suspend fun updateEsim(iccid: String, name: String? = null, isAutoTopUp: Boolean? = null, isArchived: Boolean? = null)
}
