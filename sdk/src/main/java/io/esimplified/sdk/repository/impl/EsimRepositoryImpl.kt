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
        includeBase64QrCode: Boolean,
    ): List<AssignedEsim> =
        getActiveEsims(showLegacy, isPrimary, forceRefresh, cacheTTL, includeBase64QrCode) +
            getArchivedEsims(showLegacy, isPrimary, forceRefresh, cacheTTL, includeBase64QrCode)

    override suspend fun getEsimsResult(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): RepositoryResult<List<AssignedEsim>> = combineResults(
        getActiveEsimsResult(showLegacy, isPrimary, forceRefresh, cacheTTL, includeBase64QrCode),
        getArchivedEsimsResult(showLegacy, isPrimary, forceRefresh, cacheTTL, includeBase64QrCode),
    )

    override suspend fun getActiveEsims(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): List<AssignedEsim> = fetchEsimList(
        archived = false,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
        includeBase64QrCode = includeBase64QrCode,
    ).listOrThrow()

    override suspend fun getActiveEsimsResult(
        showLegacy: Boolean,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): RepositoryResult<List<AssignedEsim>> = fetchEsimList(
        archived = false,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
        includeBase64QrCode = includeBase64QrCode,
    )

    override suspend fun getArchivedEsims(
        showLegacy: Boolean?,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): List<AssignedEsim> = fetchEsimList(
        archived = true,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
        includeBase64QrCode = includeBase64QrCode,
    ).listOrThrow()

    override suspend fun getArchivedEsimsResult(
        showLegacy: Boolean?,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): RepositoryResult<List<AssignedEsim>> = fetchEsimList(
        archived = true,
        showLegacy = showLegacy,
        isPrimary = isPrimary,
        forceRefresh = forceRefresh,
        cacheTTL = cacheTTL,
        includeBase64QrCode = includeBase64QrCode,
    )

    override suspend fun getEsimByIccid(
        iccid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): AssignedEsim = getEsimByIccidResult(iccid, forceRefresh, cacheTTL, includeBase64QrCode).valueOrThrow()

    override suspend fun getEsimByIccidResult(
        iccid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): RepositoryResult<AssignedEsim?> =
        cache.cachedResult<AssignedEsim>(
            esimDetailsKey(iccid, includeBase64QrCode),
            forceRefresh,
            cacheTTL,
        ) {
            try {
                apiService.getCustomerEsimByICCID(
                    iccid = iccid,
                    includeBase64QrCode = true.takeIf { includeBase64QrCode }
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
        cache.remove(esimDetailsKey(iccid, includeBase64QrCode = false))
        cache.remove(esimDetailsKey(iccid, includeBase64QrCode = true))
        cache.removeWithPrefix(ESIM_LIST_KEY_PREFIX)
    }

    private fun esimListKey(
        archived: Boolean,
        showLegacy: Boolean?,
        isPrimary: Boolean?,
        includeBase64QrCode: Boolean,
    ): String =
        "$ESIM_LIST_KEY_PREFIX${archived}_legacy${showLegacy?.toString() ?: UNSET_SHOW_LEGACY}" +
            "_primary${isPrimary?.toString() ?: UNSET_IS_PRIMARY}_qr$includeBase64QrCode"

    private fun esimDetailsKey(iccid: String, includeBase64QrCode: Boolean): String =
        "$ESIM_DETAILS_KEY_PREFIX${iccid}_qr$includeBase64QrCode"
    // endregion

    private suspend fun fetchEsimList(
        archived: Boolean,
        showLegacy: Boolean?,
        isPrimary: Boolean?,
        forceRefresh: Boolean,
        cacheTTL: Duration,
        includeBase64QrCode: Boolean,
    ): RepositoryResult<List<AssignedEsim>> =
        cache.cachedListResult(
            esimListKey(archived, showLegacy, isPrimary, includeBase64QrCode),
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
                    isPrimary = isPrimary,
                    orderBy = LIST_ORDER_BY,
                    limit = LIST_LIMIT,
                    includeBase64QrCode = true.takeIf { includeBase64QrCode }
                ).results
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }

    private companion object {
        const val ESIM_LIST_KEY_PREFIX = "esims_"
        const val ESIM_DETAILS_KEY_PREFIX = "esim_details_"
        const val UNSET_IS_PRIMARY = "any"
        const val UNSET_SHOW_LEGACY = "unset"
        const val LIST_ORDER_BY = "-assigned_date"
        const val LIST_LIMIT = 1000
        const val UPDATE_SUCCEEDED_MESSAGE = "eSIM updated successfully"
        const val UPDATE_FAILED_MESSAGE = "The update did not succeed"
    }
}
