package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PackagesRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.RatingApiResponse
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.cachedResult
import io.esimplified.sdk.repository.listOrThrow
import io.esimplified.sdk.repository.valueOrThrow
import kotlin.time.Duration
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class PackagesRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : PackagesRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

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
            try {
                apiService.getPackageListBy(
                    code = destination.code,
                    name = destination.name,
                    slug = destination.slug
                ).results
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
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
            try {
                apiService.getEsimTopUpPackages(iccid = iccid).results
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
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
            try {
                apiService.getPackageStock(packageTypeId = packageTypeId)
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }
    // endregion

    // region Rating
    override suspend fun getPackageRating(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RatingApiResponse = getPackageRatingResult(forceRefresh, cacheTTL).valueOrThrow()

    override suspend fun getPackageRatingResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<RatingApiResponse?> =
        cache.cachedResult<RatingApiResponse>(PACKAGE_RATING_KEY, forceRefresh, cacheTTL) {
            try {
                apiService.getPackageRating()
            } catch (e: HttpException) {
                throw Exception(parseHttpError(e) ?: e.message)
            }
        }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(PACKAGES_KEY_PREFIX)
        cache.removeWithPrefix(CHECK_STOCK_KEY_PREFIX)
    }

    private fun packagesKey(destination: Destination): String =
        "$PACKAGES_KEY_PREFIX${destination.code}_${destination.name}_${destination.slug}"

    private fun topUpPackagesKey(iccid: String): String = "${PACKAGES_KEY_PREFIX}topup_$iccid"

    private fun checkStockKey(packageTypeId: Int): String = "$CHECK_STOCK_KEY_PREFIX$packageTypeId"
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

    private companion object {
        const val PACKAGES_KEY_PREFIX = "packages_"
        const val CHECK_STOCK_KEY_PREFIX = "check_stock_"
        const val PACKAGE_RATING_KEY = "packages_rating"
    }
}
