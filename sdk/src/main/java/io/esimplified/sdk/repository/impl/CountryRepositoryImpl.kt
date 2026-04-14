package io.esimplified.sdk.repository.impl

import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.Country
import io.esimplified.sdk.network.ApiService

internal class CountryRepositoryImpl(
    private val apiService: ApiService
) : CountryRepository {

    // region Countries
    override suspend fun getCountries(): List<Country> {
        return apiService.getCountryList().results
    }

    override suspend fun getCountriesBy(destination: Destination): List<Country> {
        return apiService.getCountryListBy(
            code = destination.code,
            name = destination.name,
            region = destination.region
        ).results
    }

    override suspend fun search(query: String): List<Country> {
        return apiService.search(query = query).results
    }
    // endregion

    // region Location
    override suspend fun getUserLocation(): UserLocationResponse {
        return apiService.getUserLocation()
    }
    // endregion
}
