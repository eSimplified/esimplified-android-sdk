package io.esimplified.sdk.repository

import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.RatingApiResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface PackagesRepository {

    // region Reads
    suspend fun getPackages(
        destination: Destination,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): List<PackagePlan>

    suspend fun getTopUpPackages(
        iccid: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): List<PackagePlan>

    suspend fun checkStock(
        packageTypeId: Int,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): CheckStockResponse

    suspend fun getPackageRating(
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): RatingApiResponse
    // endregion

    // region Result reads
    suspend fun getPackagesResult(
        destination: Destination,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): RepositoryResult<List<PackagePlan>> =
        RepositoryResult(getPackages(destination, forceRefresh, cacheTTL))

    suspend fun getTopUpPackagesResult(
        iccid: String,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): RepositoryResult<List<PackagePlan>> =
        RepositoryResult(getTopUpPackages(iccid, forceRefresh, cacheTTL))

    suspend fun checkStockResult(
        packageTypeId: Int,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): RepositoryResult<CheckStockResponse?> =
        RepositoryResult(checkStock(packageTypeId, forceRefresh, cacheTTL))

    suspend fun getPackageRatingResult(
        forceRefresh: Boolean = false,
        cacheTTL: Duration = PACKAGES_TTL,
    ): RepositoryResult<RatingApiResponse?> =
        RepositoryResult(getPackageRating(forceRefresh, cacheTTL))
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val PACKAGES_TTL: Duration = 3600.seconds
    }
}
