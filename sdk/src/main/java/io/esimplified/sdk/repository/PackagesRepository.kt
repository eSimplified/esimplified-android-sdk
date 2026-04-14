package io.esimplified.sdk.repository

import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.RatingApiResponse

interface PackagesRepository {
    suspend fun getPackages(destination: Destination): List<PackagePlan>
    suspend fun getTopUpPackages(iccid: String): List<PackagePlan>
    suspend fun checkStock(packageTypeId: Int): CheckStockResponse
    suspend fun getPackageRating(): RatingApiResponse
}
