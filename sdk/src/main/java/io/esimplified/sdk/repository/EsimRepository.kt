package io.esimplified.sdk.repository

import io.esimplified.sdk.model.AssignedEsim
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface EsimRepository {

    // region Reads
    suspend fun getEsims(
        showLegacy: Boolean = true,
        isPrimary: Boolean? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_LIST_TTL,
    ): List<AssignedEsim>

    suspend fun getActiveEsims(
        showLegacy: Boolean = true,
        isPrimary: Boolean? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_LIST_TTL,
    ): List<AssignedEsim>

    suspend fun getArchivedEsims(
        showLegacy: Boolean = true,
        isPrimary: Boolean? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_LIST_TTL,
    ): List<AssignedEsim>

    suspend fun getEsimByIccid(
        iccid: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_DETAILS_TTL,
    ): AssignedEsim
    // endregion

    // region Result reads
    suspend fun getEsimsResult(
        showLegacy: Boolean = true,
        isPrimary: Boolean? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_LIST_TTL,
    ): RepositoryResult<List<AssignedEsim>> =
        RepositoryResult(getEsims(showLegacy, isPrimary, forceRefresh, cacheTTL))

    suspend fun getActiveEsimsResult(
        showLegacy: Boolean = true,
        isPrimary: Boolean? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_LIST_TTL,
    ): RepositoryResult<List<AssignedEsim>> =
        RepositoryResult(getActiveEsims(showLegacy, isPrimary, forceRefresh, cacheTTL))

    suspend fun getArchivedEsimsResult(
        showLegacy: Boolean = true,
        isPrimary: Boolean? = null,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_LIST_TTL,
    ): RepositoryResult<List<AssignedEsim>> =
        RepositoryResult(getArchivedEsims(showLegacy, isPrimary, forceRefresh, cacheTTL))

    suspend fun getEsimByIccidResult(
        iccid: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = ESIM_DETAILS_TTL,
    ): RepositoryResult<AssignedEsim?> =
        RepositoryResult(getEsimByIccid(iccid, forceRefresh, cacheTTL))
    // endregion

    // region Writes
    suspend fun updateEsim(
        iccid: String,
        name: String? = null,
        isAutoTopUp: Boolean? = null,
        isArchived: Boolean? = null,
        isPrimary: Boolean? = null,
    )

    suspend fun updateEsimPrimaryStatus(iccid: String, isPrimary: Boolean) =
        updateEsim(iccid = iccid, isPrimary = isPrimary)
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val ESIM_LIST_TTL: Duration = 86400.seconds
        val ESIM_DETAILS_TTL: Duration = 300.seconds
    }
}
