package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.random.Random

/**
 * Demo provider: generates synthetic zones around the CURRENTLY SELECTED city center.
 * All values are explicitly marked isDemo=true / isRealData=false.
 */
@Singleton
class DemoDemandCoefficientProvider @Inject constructor() : DemandCoefficientProvider {

    override val providerId: String = "demo"
    override val displayName: String = "Демонстрационный поставщик"

    private val cache = ConcurrentHashMap<String, List<DemandZone>>()
    private val flows = ConcurrentHashMap<String, MutableStateFlow<List<DemandZone>>>()
    private var lastUpdated: Long? = null
    private val cityRegistry = ConcurrentHashMap<String, City>()

    fun registerCity(city: City) {
        cityRegistry[city.id] = city
    }

    override suspend fun getSupportedCountries(): List<Country> =
        cityRegistry.values.map { it.country }.distinctBy { it.code }

    override suspend fun getSupportedCities(countryCode: String): List<City> =
        cityRegistry.values.filter { it.country.code.equals(countryCode, ignoreCase = true) }

    override suspend fun detectCity(latitude: Double, longitude: Double): City? {
        // Demo never invents a "real" city — reverse geocoding lives elsewhere.
        return cityRegistry.values.minByOrNull {
            GeoMath.distanceKm(it.center, GeoPoint(latitude, longitude))
        }?.takeIf {
            GeoMath.distanceKm(it.center, GeoPoint(latitude, longitude)) < 80
        }
    }

    override suspend fun getDemandZones(cityId: String, mapBounds: GeoBounds?): List<DemandZone> {
        delay(350)
        val city = cityRegistry[cityId]
        val zones = if (city != null) {
            generateForCity(city)
        } else {
            emptyList()
        }
        cache[cityId] = zones
        lastUpdated = System.currentTimeMillis()
        flows.getOrPut(cityId) { MutableStateFlow(zones) }.value = zones
        return zones
    }

    override suspend fun getZoneDetails(zoneId: String): DemandZone? =
        cache.values.flatten().firstOrNull { it.id == zoneId }

    override fun observeDemandZones(cityId: String): Flow<List<DemandZone>> =
        flows.getOrPut(cityId) { MutableStateFlow(cache[cityId].orEmpty()) }.asStateFlow()

    override suspend fun getLastUpdatedAt(): Long? = lastUpdated

    override suspend fun getProviderStatus(): ProviderStatus = ProviderStatus(
        name = displayName,
        sourceType = SourceType.DEMO_PROVIDER,
        status = ProviderConnectionStatus.DEMO,
        lastUpdatedAtEpochMs = lastUpdated,
        supportedCitiesHint = "Любой выбранный город (синтетические зоны)",
        termsOfUse = "Только для демонстрации UI. Не использовать для рабочих решений.",
        isDemo = true
    )

    override suspend fun isRealTimeDataAvailable(cityId: String): Boolean = false

    private fun generateForCity(city: City): List<DemandZone> {
        val rnd = Random(city.id.hashCode() + (System.currentTimeMillis() / 300_000))
        val now = System.currentTimeMillis()
        val districtHints = listOf(
            "Центр", "Север", "Юг", "Восток", "Запад",
            "Вокзал", "Аэропорт", "Бизнес-парк", "Университет", "Рынок",
            "Жилой массив", "Набережная"
        )
        val count = 6 + abs(city.id.hashCode() % 5)
        return (0 until count).map { i ->
            val bearing = (i * (360.0 / count) + rnd.nextDouble(-15.0, 15.0))
            val dist = rnd.nextDouble(1.0, 14.0)
            val center = GeoMath.offsetPoint(city.center, dist, bearing)
            val radius = rnd.nextDouble(0.6, 2.2)
            val coef = when (rnd.nextInt(100)) {
                in 0..25 -> 1.0
                in 26..55 -> rnd.nextDouble(1.1, 1.4)
                in 56..80 -> rnd.nextDouble(1.5, 1.9)
                else -> rnd.nextDouble(2.0, 2.8)
            }.let { (it * 10).toInt() / 10.0 }

            val level = when {
                coef >= 2.0 -> DemandLevel.CRITICAL
                coef >= 1.5 -> DemandLevel.HIGH
                coef >= 1.1 -> DemandLevel.ELEVATED
                else -> DemandLevel.NORMAL
            }
            val base = when (city.currencyCode) {
                "RUB" -> 350.0
                "KZT" -> 1800.0
                "BYN" -> 12.0
                "EUR" -> 6.0
                "USD" -> 7.0
                else -> 400.0
            }
            val extra = base * (coef - 1.0).coerceAtLeast(0.0)

            DemandZone(
                id = "demo-${city.id}-$i-${UUID.nameUUIDFromBytes("${city.id}-$i".toByteArray())}",
                cityId = city.id,
                districtName = "${districtHints[i % districtHints.size]} · ${city.name}",
                center = center,
                polygon = GeoMath.regularPolygon(center, radius),
                coefficient = coef,
                coefficientType = CoefficientType.SURGE,
                baseIncome = base,
                extraIncome = extra,
                fetchedAtEpochMs = now,
                validUntilEpochMs = now + 8 * 60_000L,
                sourceName = "Демо-генератор",
                sourceType = SourceType.DEMO_PROVIDER,
                isRealData = false,
                isDemo = true,
                confidence = 0.35,
                demandLevel = level,
                availableVehicleClasses = VehicleClass.configurable,
                survivalProbability = when {
                    coef >= 2.0 -> 0.5
                    coef >= 1.5 -> 0.65
                    else -> 0.8
                },
                coefficientsByClass = YaRadarOfficialEngine.multiTariffCoefficients(coef)
            )
        }
    }

    companion object {
        fun sampleCity(
            id: String,
            name: String,
            region: String,
            country: Country,
            lat: Double,
            lon: Double
        ): City = City(
            id = id,
            name = name,
            region = region,
            country = country,
            center = GeoPoint(lat, lon),
            bounds = null,
            timeZoneId = country.timeZoneId,
            currencyCode = country.currencyCode,
            availableVehicleClasses = listOf(
                VehicleClass.ECONOMY,
                VehicleClass.COMFORT,
                VehicleClass.COMFORT_PLUS,
                VehicleClass.BUSINESS
            ),
            availableTariffs = emptyList(),
            demandDataAvailable = false,
            dataAvailability = CityDataAvailability.DEMO_ONLY
        )
    }
}
