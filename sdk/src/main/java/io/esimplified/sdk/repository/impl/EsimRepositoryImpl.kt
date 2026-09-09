package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.EsimRepository

import io.esimplified.sdk.model.AssignedEsim
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.combineResults
import io.esimplified.sdk.repository.listOrThrow
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import okhttp3.ResponseBody
import retrofit2.HttpException

internal class EsimRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : EsimRepository {

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
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
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
        val response = apiService.updateEsim(
            id = iccid,
            name = name,
            isArchived = isArchived,
            autoTopUp = isAutoTopUp,
            isPrimary = isPrimary
        )
        if (!response.isSuccessful) {
            throw Exception(ApiErrorMessage.parse(readBody(response.errorBody())))
        }
        val message = ApiErrorMessage.parseOrNull(readBody(response.body()))
        if (message != UPDATE_SUCCEEDED_MESSAGE) {
            throw Exception(message ?: UPDATE_FAILED_MESSAGE)
        }
    }

    private fun readBody(body: ResponseBody?): String? =
        runCatching { body?.string() }.getOrNull()
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
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }

    private companion object {
        const val ESIM_LIST_KEY_PREFIX = "esims_"
        const val ESIM_DETAILS_KEY_PREFIX = "esim_details_"
        const val UNSET_IS_PRIMARY = "any"
        const val UPDATE_SUCCEEDED_MESSAGE = "eSIM updated successfully"
        const val UPDATE_FAILED_MESSAGE = "The update did not succeed"
    }
}
