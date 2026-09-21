package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.repository.CountryRepository

import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.Country
import io.esimplified.sdk.network.ApiService
import io.esimplified.sdk.network.SdkCache
import io.esimplified.sdk.repository.RepositoryResult
import io.esimplified.sdk.repository.cachedListResult
import io.esimplified.sdk.repository.apiRead
import io.esimplified.sdk.repository.listOrThrow
import kotlin.time.Duration

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
            apiService.getCountryList().results
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
            apiService.getCountryListBy(
                code = destination.code,
                name = destination.name,
                region = destination.region
            ).results
        }

    override suspend fun search(query: String): List<Country> =
        apiRead { apiService.search(query = query).results }
    // endregion

    // region Location
    override suspend fun getUserLocation(): UserLocationResponse =
        apiRead { apiService.getUserLocation() }
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
