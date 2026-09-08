package io.esimplified.sdk.repository

import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.Country
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

interface CountryRepository {

    // region Reads
    suspend fun getCountries(
        forceRefresh: Boolean = false,
        cacheTTL: Duration = COUNTRIES_TTL,
    ): List<Country>

    suspend fun getCountriesBy(
        destination: Destination,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = COUNTRIES_TTL,
    ): List<Country>

    suspend fun search(query: String): List<Country>

    suspend fun getUserLocation(): UserLocationResponse
    // endregion

    // region Result reads
    suspend fun getCountriesResult(
        forceRefresh: Boolean = false,
        cacheTTL: Duration = COUNTRIES_TTL,
    ): RepositoryResult<List<Country>> =
        RepositoryResult(getCountries(forceRefresh, cacheTTL))

    suspend fun getCountriesByResult(
        destination: Destination,
        forceRefresh: Boolean = false,
        cacheTTL: Duration = COUNTRIES_TTL,
    ): RepositoryResult<List<Country>> =
        RepositoryResult(getCountriesBy(destination, forceRefresh, cacheTTL))
    // endregion

    // region Cache
    suspend fun invalidateCache() = Unit
    // endregion

    companion object {
        val COUNTRIES_TTL: Duration = 86400.seconds
    }
}
