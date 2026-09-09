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
    override suspend fun getEsims(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<AssignedEsim> =
        getActiveEsims(showLegacy, isPrimary, forceRefresh, cacheTTL) +
            getArchivedEsims(showLegacy, isPrimary, forceRefresh, cacheTTL)

    override suspend fun getEsimsResult(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> = combineResults(
        getActiveEsimsResult(showLegacy, isPrimary, forceRefresh, cacheTTL),
        getArchivedEsimsResult(showLegacy, isPrimary, forceRefresh, cacheTTL),
    )

    override suspend fun getActiveEsims(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<AssignedEsim> = fetchEsimList(
        archived = false,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
    ).listOrThrow()

    override suspend fun getActiveEsimsResult(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> = fetchEsimList(
        archived = false,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
    )

    override suspend fun getArchivedEsims(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<AssignedEsim> = fetchEsimList(
        archived = true,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
    ).listOrThrow()

    override suspend fun getArchivedEsimsResult(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> = fetchEsimList(
        archived = true,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
    )

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
        isArchived: Boolean?,
        isPrimary: Boolean?
    ) {
        invalidateEsimCaches(iccid)
        try {
            apiService.updateEsim(
                id = iccid,
                name = name,
                isArchived = isArchived,
                autoTopUp = isAutoTopUp,
                isPrimary = isPrimary
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

    private fun esimListKey(archived: Boolean, showLegacy: Boolean, isPrimary: Boolean?): String =
        "$ESIM_LIST_KEY_PREFIX${archived}_legacy${showLegacy}_primary${isPrimary?.toString() ?: UNSET_IS_PRIMARY}"

    private fun esimDetailsKey(iccid: String): String = "$ESIM_DETAILS_KEY_PREFIX$iccid"
    // endregion

    private suspend fun fetchEsimList(
        archived: Boolean,
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<AssignedEsim>> =
        cache.cachedListResult(
            esimListKey(archived, showLegacy, isPrimary),
            forceRefresh,
            cacheTTL,
        ) {
            try {
                apiService.getCustomerEsimList(
                    getESimDetails = true,
                    getPackageDetails = true,
                    getBalanceRemaining = true,
                    showArchived = archived,
                    showLegacy = showLegacy,
                    isPrimary = isPrimary
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
        const val UNSET_IS_PRIMARY = "any"
    }
}
