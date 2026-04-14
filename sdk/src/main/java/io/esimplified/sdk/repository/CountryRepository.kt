package io.esimplified.sdk.repository

import io.esimplified.sdk.model.Destination
import io.esimplified.sdk.model.UserLocationResponse
import io.esimplified.sdk.model.Country

interface CountryRepository {
    suspend fun getCountries(): List<Country>
    suspend fun getCountriesBy(destination: Destination): List<Country>
    suspend fun search(query: String): List<Country>
    suspend fun getUserLocation(): UserLocationResponse
}
