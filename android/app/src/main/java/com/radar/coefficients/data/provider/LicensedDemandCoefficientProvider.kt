package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoBounds
import com.radar.coefficients.domain.model.ProviderConnectionStatus
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.provider.DemandCoefficientProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Placeholder for a licensed third-party provider.
 * Disabled until configuration is supplied via backend.
 */
@Singleton
class LicensedDemandCoefficientProvider @Inject constructor() : DemandCoefficientProvider {

    override val providerId: String = "licensed"
    override val displayName: String = "Лицензированный поставщик"

    override suspend fun getSupportedCountries(): List<Country> = emptyList()
    override suspend fun getSupportedCities(countryCode: String): List<City> = emptyList()
    override suspend fun detectCity(latitude: Double, longitude: Double): City? = null
    override suspend fun getDemandZones(cityId: String, mapBounds: GeoBounds?): List<DemandZone> = emptyList()
    override suspend fun getZoneDetails(zoneId: String): DemandZone? = null
    override fun observeDemandZones(cityId: String): Flow<List<DemandZone>> = flowOf(emptyList())
    override suspend fun getLastUpdatedAt(): Long? = null

    override suspend fun getProviderStatus(): ProviderStatus = ProviderStatus(
        name = displayName,
        sourceType = SourceType.LICENSED_PROVIDER,
        status = ProviderConnectionStatus.NOT_CONFIGURED,
        lastUpdatedAtEpochMs = null,
        supportedCitiesHint = "Будет заполнено после подключения лицензии",
        termsOfUse = "Требуется договор с поставщиком. Адаптер выключен.",
        isDemo = false
    )

    override suspend fun isRealTimeDataAvailable(cityId: String): Boolean = false
}
