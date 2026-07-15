package com.radar.coefficients.domain.repository

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoBounds
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.model.RouteEstimate
import com.radar.coefficients.domain.model.TaxiTariff
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.VehicleClass
import kotlinx.coroutines.flow.Flow

interface DemandRepository {
    suspend fun refreshZones(cityId: String, bounds: GeoBounds?): Result<List<DemandZone>>
    fun observeZones(cityId: String): Flow<List<DemandZone>>
    suspend fun getZone(zoneId: String): DemandZone?
    suspend fun getLastUpdatedAt(): Long?
    suspend fun getActiveProvidersStatus(): List<ProviderStatus>
    suspend fun isRealTimeAvailable(cityId: String): Boolean
}

interface CityRepository {
    suspend fun detectCity(latitude: Double, longitude: Double): City?
    suspend fun searchCities(query: String): List<City>
    suspend fun getCity(cityId: String): City?
    suspend fun getCountries(): List<Country>
    suspend fun getCitiesByCountry(countryCode: String): List<City>
    fun observeSelectedCity(): Flow<City?>
    suspend fun setSelectedCity(city: City)
    suspend fun addFavorite(cityId: String)
    suspend fun removeFavorite(cityId: String)
}

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun getSettings(): UserSettings
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)
}

interface RouteRepository {
    suspend fun buildRoute(origin: GeoPoint, destination: GeoPoint): Result<RouteEstimate>
    suspend fun estimateToZone(origin: GeoPoint, zoneCenter: GeoPoint): Result<RouteEstimate>
}

interface TariffRepository {
    suspend fun getTariffs(cityId: String): List<TaxiTariff>
    suspend fun getTariff(cityId: String, vehicleClass: VehicleClass): TaxiTariff?
}
