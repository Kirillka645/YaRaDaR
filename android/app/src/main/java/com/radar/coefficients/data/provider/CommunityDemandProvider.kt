package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoBounds
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.ProviderConnectionStatus
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.provider.DemandCoefficientProvider
import com.radar.coefficients.domain.util.GeoMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voluntary driver reports. Always marked as community data, never official.
 */
@Singleton
class CommunityDemandProvider @Inject constructor() : DemandCoefficientProvider {

    override val providerId: String = "community"
    override val displayName: String = "Сообщения водителей"

    data class DriverReport(
        val id: String = UUID.randomUUID().toString(),
        val cityId: String,
        val districtName: String,
        val location: GeoPoint,
        val coefficient: Double,
        val reportedAtEpochMs: Long = System.currentTimeMillis(),
        val trustScore: Double = 0.5
    )

    private val reports = ConcurrentHashMap<String, MutableList<DriverReport>>()
    private val flow = MutableStateFlow<List<DemandZone>>(emptyList())
    private var lastUpdated: Long? = null

    fun submitReport(report: DriverReport) {
        reports.getOrPut(report.cityId) { mutableListOf() }.add(report)
        lastUpdated = System.currentTimeMillis()
        flow.value = reports.values.flatten().map { it.toZone() }
    }

    override suspend fun getSupportedCountries(): List<Country> = emptyList()
    override suspend fun getSupportedCities(countryCode: String): List<City> = emptyList()
    override suspend fun detectCity(latitude: Double, longitude: Double): City? = null

    override suspend fun getDemandZones(cityId: String, mapBounds: GeoBounds?): List<DemandZone> {
        val list = reports[cityId].orEmpty().map { it.toZone() }
        flow.value = list
        return list
    }

    override suspend fun getZoneDetails(zoneId: String): DemandZone? =
        reports.values.flatten().map { it.toZone() }.firstOrNull { it.id == zoneId }

    override fun observeDemandZones(cityId: String): Flow<List<DemandZone>> = flow.asStateFlow()

    override suspend fun getLastUpdatedAt(): Long? = lastUpdated

    override suspend fun getProviderStatus(): ProviderStatus = ProviderStatus(
        name = displayName,
        sourceType = SourceType.MANUAL_DRIVER_REPORT,
        status = ProviderConnectionStatus.CONNECTED,
        lastUpdatedAtEpochMs = lastUpdated,
        supportedCitiesHint = "Города, где водители оставили отчёты",
        termsOfUse = "Добровольные сообщения. Не являются официальными коэффициентами.",
        isDemo = false
    )

    override suspend fun isRealTimeDataAvailable(cityId: String): Boolean =
        reports[cityId].orEmpty().isNotEmpty()

    private fun DriverReport.toZone(): DemandZone {
        val level = when {
            coefficient >= 2.0 -> DemandLevel.CRITICAL
            coefficient >= 1.5 -> DemandLevel.HIGH
            coefficient >= 1.1 -> DemandLevel.ELEVATED
            else -> DemandLevel.NORMAL
        }
        return DemandZone(
            id = "community-$id",
            cityId = cityId,
            districtName = districtName,
            center = location,
            polygon = GeoMath.regularPolygon(location, 0.8),
            coefficient = coefficient,
            coefficientType = CoefficientType.SURGE,
            baseIncome = 0.0,
            extraIncome = 0.0,
            fetchedAtEpochMs = reportedAtEpochMs,
            validUntilEpochMs = reportedAtEpochMs + 20 * 60_000L,
            sourceName = "Сообщение водителя",
            sourceType = SourceType.MANUAL_DRIVER_REPORT,
            isRealData = false,
            isDemo = false,
            confidence = trustScore.coerceIn(0.1, 0.75),
            demandLevel = level,
            availableVehicleClasses = listOf(VehicleClass.ECONOMY),
            survivalProbability = null
        )
    }
}
