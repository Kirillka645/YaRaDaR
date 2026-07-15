package com.radar.coefficients.domain.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoBounds
import com.radar.coefficients.domain.model.ProviderStatus
import kotlinx.coroutines.flow.Flow

/**
 * Contract for legal demand-coefficient data sources.
 * Implementations must never scrape closed apps or intercept traffic.
 */
interface DemandCoefficientProvider {
    val providerId: String
    val displayName: String

    suspend fun getSupportedCountries(): List<Country>
    suspend fun getSupportedCities(countryCode: String): List<City>
    suspend fun detectCity(latitude: Double, longitude: Double): City?
    suspend fun getDemandZones(cityId: String, mapBounds: GeoBounds?): List<DemandZone>
    suspend fun getZoneDetails(zoneId: String): DemandZone?
    fun observeDemandZones(cityId: String): Flow<List<DemandZone>>
    suspend fun getLastUpdatedAt(): Long?
    suspend fun getProviderStatus(): ProviderStatus
    suspend fun isRealTimeDataAvailable(cityId: String): Boolean
}
