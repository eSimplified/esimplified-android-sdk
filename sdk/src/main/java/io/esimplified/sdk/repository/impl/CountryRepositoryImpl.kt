package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.CountryRepository

import io.esimplified.sdk.model.ApiErrorResponse
import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.Country
import io.esimplified.sdk.network.ApiService
import kotlinx.serialization.json.Json
import retrofit2.HttpException

internal class CountryRepositoryImpl(
    private val apiService: ApiService
) : CountryRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // region Countries
    override suspend fun getCountries(): List<Country> {
        try {
            return apiService.getCountryList().results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun getCountriesBy(destination: Destination): List<Country> {
        try {
            return apiService.getCountryListBy(
                code = destination.code,
                name = destination.name,
                region = destination.region
            ).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }

    override suspend fun search(query: String): List<Country> {
        try {
            return apiService.search(query = query).results
        } catch (e: HttpException) {
            throw Exception(parseHttpError(e) ?: e.message)
        }
    }
    // endregion

    // region Location
    override suspend fun getUserLocation(): UserLocationResponse {
        try {
            return apiService.getUserLocation()
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
