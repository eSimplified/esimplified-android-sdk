package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.EsimRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.AssignedEsim
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.combineResults
import io.esimplified.sdk.repository.listOrThrow
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class EsimRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : EsimRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region eSIMs
    override suspend fun getEsims(forceRefresh: Boolean, cacheTTL: Duration): List<AssignedEsim> =
        getActiveEsims(forceRefresh, cacheTTL) + getArchivedEsims(forceRefresh, cacheTTL)

    override suspend fun getEsimsResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> = combineResults(
        getActiveEsimsResult(forceRefresh, cacheTTL),
        getArchivedEsimsResult(forceRefresh, cacheTTL),
    )

    override suspend fun getActiveEsims(forceRefresh: Boolean, cacheTTL: Duration): List<AssignedEsim> =
        fetchEsimList(archived = false, forceRefresh = forceRefresh, cacheTTL = cacheTTL).listOrThrow()

    override suspend fun getActiveEsimsResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> =
        fetchEsimList(archived = false, forceRefresh = forceRefresh, cacheTTL = cacheTTL)

    override suspend fun getArchivedEsims(forceRefresh: Boolean, cacheTTL: Duration): List<AssignedEsim> =
        fetchEsimList(archived = true, forceRefresh = forceRefresh, cacheTTL = cacheTTL).listOrThrow()

    override suspend fun getArchivedEsimsResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> =
        fetchEsimList(archived = true, forceRefresh = forceRefresh, cacheTTL = cacheTTL)

    override suspend fun getEsimByIccid(
        iccid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): AssignedEsim = getEsimByIccidResult(iccid, forceRefresh, cacheTTL).valueOrThrow()

    override suspend fun getEsimByIccidResult(
        iccid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<AssignedEsim?> =
        cache.cachedResult<AssignedEsim>(esimDetailsKey(iccid), forceRefresh, cacheTTL) {
            try {
                apiService.getCustomerEsimByICCID(
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
        invalidateEsimCaches(iccid)
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

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(ESIM_LIST_KEY_PREFIX)
        cache.removeWithPrefix(ESIM_DETAILS_KEY_PREFIX)
    }

    private fun invalidateEsimCaches(iccid: String) {
        cache.remove(esimDetailsKey(iccid))
        cache.removeWithPrefix(ESIM_LIST_KEY_PREFIX)
    }

    private fun esimListKey(archived: Boolean): String =
        "$ESIM_LIST_KEY_PREFIX${archived}_legacy${SHOW_LEGACY}_primary$IS_PRIMARY"

    private fun esimDetailsKey(iccid: String): String = "$ESIM_DETAILS_KEY_PREFIX$iccid"
    // endregion

    private suspend fun fetchEsimList(
        archived: Boolean,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> =
        cache.cachedListResult(esimListKey(archived), forceRefresh, cacheTTL) {
            try {
                apiService.getCustomerEsimList(
                    getESimDetails = true,
                    getPackageDetails = true,
                    getBalanceRemaining = true,
                    showArchived = archived
                ).results
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }

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

    private companion object {
        const val ESIM_LIST_KEY_PREFIX = "esims_"
        const val ESIM_DETAILS_KEY_PREFIX = "esim_details_"
        const val SHOW_LEGACY = true
        const val IS_PRIMARY = "any"
    }
}
