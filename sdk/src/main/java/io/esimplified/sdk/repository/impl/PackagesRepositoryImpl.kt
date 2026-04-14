package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.RatingApiResponse
import io.esimplified.sdk.network.ApiService

internal class PackagesRepositoryImpl(
    private val apiService: ApiService
) : PackagesRepository {

    // region Packages
    override suspend fun getPackages(destination: Destination): List<PackagePlan> {
        return apiService.getPackageListBy(
            code = destination.code,
            name = destination.name,
            slug = destination.slug
        ).results
    }

    override suspend fun getTopUpPackages(iccid: String): List<PackagePlan> {
        return apiService.getEsimTopUpPackages(iccid = iccid).results
    }
    // endregion

    // region Stock
    override suspend fun checkStock(packageTypeId: Int): CheckStockResponse {
        return apiService.getPackageStock(packageTypeId = packageTypeId)
    }
    // endregion

    // region Rating
    override suspend fun getPackageRating(): RatingApiResponse {
        return apiService.getPackageRating()
    }
    // endregion
}
