package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PackagesRepository

import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.PackagesPage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.listOrThrow
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration

internal class PackagesRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : PackagesRepository {

    // region Packages
    override suspend fun getPackages(
        destination: Destination,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<PackagePlan> = getPackagesResult(destination, forceRefresh, cacheTTL).listOrThrow()

    override suspend fun getPackagesResult(
        destination: Destination,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<PackagePlan>> =
        cache.cachedListResult(packagesKey(destination), forceRefresh, cacheTTL) {
            apiService.getPackageListBy(
                code = destination.code,
                name = destination.name,
                slug = destination.slug
            ).results
        }

    override suspend fun getPackagesPage(
        destination: Destination,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): PackagesPage = getPackagesPageResult(destination, forceRefresh, cacheTTL).value ?: PackagesPage()

    override suspend fun getPackagesPageResult(
        destination: Destination,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<PackagesPage> {
        val result = cache.cachedResult<PackagesPage>(
            packagesPageKey(destination),
            forceRefresh,
            cacheTTL,
        ) {
            val response = apiService.getPackageListBy(
                code = destination.code,
                name = destination.name,
                slug = destination.slug
            )
            PackagesPage(
                packages = response.results,
                totalCount = response.count,
                promoCode = response.promoCode,
            )
        }
        return RepositoryResult(
            value = result.value ?: PackagesPage(),
            isStale = result.isStale,
            failure = result.failure,
        )
    }

    override suspend fun getTopUpPackages(
        iccid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<PackagePlan> = getTopUpPackagesResult(iccid, forceRefresh, cacheTTL).listOrThrow()

    override suspend fun getTopUpPackagesResult(
        iccid: String,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<PackagePlan>> =
        cache.cachedListResult(topUpPackagesKey(iccid), forceRefresh, cacheTTL) {
            apiService.getEsimTopUpPackages(iccid = iccid).results
        }
    // endregion

    // region Stock
    override suspend fun checkStock(
        packageTypeId: Int,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): CheckStockResponse = checkStockResult(packageTypeId, forceRefresh, cacheTTL).valueOrThrow()

    override suspend fun checkStockResult(
        packageTypeId: Int,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<CheckStockResponse?> =
        cache.cachedResult<CheckStockResponse>(checkStockKey(packageTypeId), forceRefresh, cacheTTL) {
            apiService.getPackageStock(packageTypeId = packageTypeId)
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(PACKAGES_KEY_PREFIX)
        cache.removeWithPrefix(CHECK_STOCK_KEY_PREFIX)
    }

    private fun packagesKey(destination: Destination): String =
        "$PACKAGES_KEY_PREFIX${destination.code}_${destination.name}_${destination.slug}"

    private fun packagesPageKey(destination: Destination): String =
        "${PACKAGES_KEY_PREFIX}page_${destination.code}_${destination.name}_${destination.slug}"

    private fun topUpPackagesKey(iccid: String): String = "${PACKAGES_KEY_PREFIX}topup_$iccid"

    private fun checkStockKey(packageTypeId: Int): String = "$CHECK_STOCK_KEY_PREFIX$packageTypeId"
    // endregion

    private companion object {
        const val PACKAGES_KEY_PREFIX = "packages_"
        const val CHECK_STOCK_KEY_PREFIX = "check_stock_"
    }
}
