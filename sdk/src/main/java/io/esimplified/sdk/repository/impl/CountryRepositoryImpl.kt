package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.CountryRepository

import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.Country
import io.esimplified.sdk.network.ApiErrorMessage
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.listOrThrow
import kotlin.time.Duration
import retrofit2.HttpException

internal class CountryRepositoryImpl(
    private val apiService: ApiService,
    private val cache: SdkCache,
) : CountryRepository {

    // region Countries
    override suspend fun getCountries(forceRefresh: Boolean, cacheTTL: Duration): List<Country> =
        getCountriesResult(forceRefresh, cacheTTL).listOrThrow()

    override suspend fun getCountriesResult(
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<Country>> =
        cache.cachedListResult(ALL_COUNTRIES_KEY, forceRefresh, cacheTTL) {
            try {
                apiService.getCountryList().results
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }

    override suspend fun getCountriesBy(
        destination: Destination,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): List<Country> = getCountriesByResult(destination, forceRefresh, cacheTTL).listOrThrow()

    override suspend fun getCountriesByResult(
        destination: Destination,
        forceRefresh: Boolean,
        cacheTTL: Duration,
    ): RepositoryResult<List<Country>> =
        cache.cachedListResult(countriesByKey(destination), forceRefresh, cacheTTL) {
            try {
                apiService.getCountryListBy(
                    code = destination.code,
                    name = destination.name,
                    region = destination.region
                ).results
            } catch (e: HttpException) {
                throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
            }
        }

    override suspend fun search(query: String): List<Country> {
        try {
            return apiService.search(query = query).results
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }
    // endregion

    // region Location
    override suspend fun getUserLocation(): UserLocationResponse {
        try {
            return apiService.getUserLocation()
        } catch (e: HttpException) {
            throw Exception(ApiErrorMessage.parseOrNull(e) ?: e.message)
        }
    }
    // endregion

    // region Cache
    override suspend fun invalidateCache() {
        cache.removeWithPrefix(COUNTRIES_KEY_PREFIX)
    }

    private fun countriesByKey(destination: Destination): String =
        "${COUNTRIES_KEY_PREFIX}by_${destination.code}_${destination.name}_${destination.region}"
    // endregion

    private companion object {
        const val COUNTRIES_KEY_PREFIX = "countries_"
        const val ALL_COUNTRIES_KEY = "countries_all"
    }
}
