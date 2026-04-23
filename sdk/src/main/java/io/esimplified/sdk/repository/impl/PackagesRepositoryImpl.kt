package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.PackagesRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.CheckStockResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.PackagePlan
import io.esimplified.sdk.model.RatingApiResponse
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class PackagesRepositoryImpl(
    private val apiService: ApiService
) : PackagesRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Packages
    override suspend fun getPackages(destination: Destination): List<PackagePlan> {
        try {
            return apiService.getPackageListBy(
                code = destination.code,
                name = destination.name,
                slug = destination.slug
            ).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getTopUpPackages(iccid: String): List<PackagePlan> {
        try {
            return apiService.getEsimTopUpPackages(iccid = iccid).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    // region Stock
    override suspend fun checkStock(packageTypeId: Int): CheckStockResponse {
        try {
            return apiService.getPackageStock(packageTypeId = packageTypeId)
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    // region Rating
    override suspend fun getPackageRating(): RatingApiResponse {
        try {
            return apiService.getPackageRating()
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
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
}
