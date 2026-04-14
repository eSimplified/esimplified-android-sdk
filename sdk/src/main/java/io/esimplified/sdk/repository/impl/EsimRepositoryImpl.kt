package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.EsimRepository

import io.esimplified.sdk.model.AssignedEsim
import io.esimplified.sdk.network.ApiService

internal class EsimRepositoryImpl(
    private val apiService: ApiService
) : EsimRepository {

    // region eSIMs
    override suspend fun getEsims(): List<AssignedEsim> {
        val active = apiService.getCustomerEsimList(
            getESimDetails = true,
            getPackageDetails = true,
            getBalanceRemaining = true,
            showArchived = false
        ).results

        val archived = apiService.getCustomerEsimList(
            getESimDetails = true,
            getPackageDetails = true,
            getBalanceRemaining = true,
            showArchived = true
        ).results

        return active + archived
    }

    override suspend fun getEsimByIccid(iccid: String): AssignedEsim {
        return apiService.getCustomerEsimByICCID(
            iccid = iccid,
            getESimDetails = true,
            getPackageDetails = true,
            getBalanceRemaining = true
        )
    }

    override suspend fun updateEsim(
        iccid: String,
        name: String?,
        isAutoTopUp: Boolean?,
        isArchived: Boolean?
    ) {
        apiService.updateEsim(
            id = iccid,
            name = name,
            isArchived = isArchived,
            autoTopUp = isAutoTopUp
        )
    }
    // endregion
}
