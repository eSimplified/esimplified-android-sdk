package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.EsimRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.AssignedEsim
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class EsimRepositoryImpl(
    private val apiService: ApiService
) : EsimRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region eSIMs
    override suspend fun getEsims(): List<AssignedEsim> {
        try {
            return getActiveEsims() + getArchivedEsims()
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getActiveEsims(): List<AssignedEsim> {
        try {
            return apiService.getCustomerEsimList(
                getESimDetails = true,
                getPackageDetails = true,
                getBalanceRemaining = true,
                showArchived = false
            ).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getArchivedEsims(): List<AssignedEsim> {
        try {
            return apiService.getCustomerEsimList(
                getESimDetails = true,
                getPackageDetails = true,
                getBalanceRemaining = true,
                showArchived = true
            ).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getEsimByIccid(iccid: String): AssignedEsim {
        try {
            return apiService.getCustomerEsimByICCID(
                iccid = iccid,
                getESimDetails = true,
                getPackageDetails = true,
                getBalanceRemaining = true
            )
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun updateEsim(
        iccid: String,
        name: String?,
        isAutoTopUp: Boolean?,
        isArchived: Boolean?
    ) {
        try {
            apiService.updateEsim(
                id = iccid,
                name = name,
                isArchived = isArchived,
                autoTopUp = isAutoTopUp
            )
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    private fun parseHttpError(e: HttpException): String? {
        return try {
            val errorBody = e.response()?.errorBody()?.string()
            if (errorBody != null) {
                val errorResponse = json.decodeFromString<ApiErrorResponse>(errorBody)
                errorResponse.detail ?: errorResponse.message ?: errorResponse.error
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
