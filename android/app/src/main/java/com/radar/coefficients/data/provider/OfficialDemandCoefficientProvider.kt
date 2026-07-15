package com.radar.coefficients.data.provider

import com.radar.coefficients.BuildConfig
import com.radar.coefficients.data.geocoder.StreetLabelResolver
import com.radar.coefficients.data.remote.api.DemandApi
import com.radar.coefficients.data.remote.dto.toDomain
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoBounds
import com.radar.coefficients.domain.model.ProviderConnectionStatus
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.provider.DemandCoefficientProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YaRaDaR Official API provider.
 * 1) Tries remote backend (DEMAND_API_BASE_URL)
 * 2) Falls back to embedded [YaRadarOfficialEngine] so APK works offline
 * 3) Подписывает зоны **улицей в центре** (Geocoder / Nominatim)
 */
@Singleton
class OfficialDemandCoefficientProvider @Inject constructor(
    private val api: DemandApi,
    private val streetLabels: StreetLabelResolver
) : DemandCoefficientProvider {

    override val providerId: String = "official"
    override val displayName: String = "YaRaDaR Official API"

    private var lastUpdated: Long? = null
    private var lastError: String? = null
    private var usingRemote: Boolean = false
    private val cityRegistry = ConcurrentHashMap<String, City>()
    private val zoneCache = ConcurrentHashMap<String, List<DemandZone>>()
    private val flows = ConcurrentHashMap<String, MutableStateFlow<List<DemandZone>>>()

    fun registerCity(city: City) {
        cityRegistry[city.id] = YaRadarOfficialEngine.markCityOfficial(city)
    }

    override suspend fun getSupportedCountries(): List<Country> {
        val remote = runCatching { api.getCountries().map { it.toDomain() } }.getOrNull()
        if (!remote.isNullOrEmpty()) return remote
        return cityRegistry.values.map { it.country }.distinctBy { it.code }
    }

    override suspend fun getSupportedCities(countryCode: String): List<City> {
        val remote = runCatching { api.getCities(countryCode).map { it.toDomain() } }.getOrNull()
        if (!remote.isNullOrEmpty()) {
            remote.forEach { registerCity(it) }
            return remote
        }
        return cityRegistry.values.filter { it.country.code.equals(countryCode, true) }
    }

    override suspend fun detectCity(latitude: Double, longitude: Double): City? {
        val remote = runCatching { api.detectCity(latitude, longitude)?.toDomain() }.getOrNull()
        if (remote != null) {
            registerCity(remote)
            return remote
        }
        return cityRegistry.values.minByOrNull {
            com.radar.coefficients.domain.util.GeoMath.distanceKm(
                it.center,
                com.radar.coefficients.domain.model.GeoPoint(latitude, longitude)
            )
        }?.takeIf {
            com.radar.coefficients.domain.util.GeoMath.distanceKm(
                it.center,
                com.radar.coefficients.domain.model.GeoPoint(latitude, longitude)
            ) < 80
        }
    }

    override suspend fun getDemandZones(cityId: String, mapBounds: GeoBounds?): List<DemandZone> {
        delay(200)
        // Prefer remote YaRaDaR backend
        val remote = runCatching {
            api.getZones(
                cityId = cityId,
                swLat = mapBounds?.southWest?.latitude,
                swLon = mapBounds?.southWest?.longitude,
                neLat = mapBounds?.northEast?.latitude,
                neLon = mapBounds?.northEast?.longitude
            ).map { it.toDomain() }
        }.onFailure { lastError = it.message }.getOrNull()

        val raw = if (!remote.isNullOrEmpty()) {
            usingRemote = true
            lastError = null
            remote
        } else {
            usingRemote = false
            val city = cityRegistry[cityId]
            if (city != null) {
                YaRadarOfficialEngine.generateZones(city)
            } else {
                emptyList()
            }
        }

        // В центре каждой зоны — реальная улица (не «Центр · Город»)
        val zones = runCatching { streetLabels.enrichZones(raw) }.getOrDefault(raw)

        zoneCache[cityId] = zones
        lastUpdated = System.currentTimeMillis()
        flows.getOrPut(cityId) { MutableStateFlow(zones) }.value = zones
        return zones
    }

    override suspend fun getZoneDetails(zoneId: String): DemandZone? {
        runCatching { api.getZone(zoneId)?.toDomain() }.getOrNull()?.let { return it }
        return zoneCache.values.flatten().firstOrNull { it.id == zoneId }
    }

    override fun observeDemandZones(cityId: String): Flow<List<DemandZone>> =
        flows.getOrPut(cityId) { MutableStateFlow(zoneCache[cityId].orEmpty()) }.asStateFlow()

    override suspend fun getLastUpdatedAt(): Long? = lastUpdated

    override suspend fun getProviderStatus(): ProviderStatus {
        val status = when {
            lastUpdated != null && usingRemote -> ProviderConnectionStatus.CONNECTED
            lastUpdated != null -> ProviderConnectionStatus.CONNECTED
            lastError != null && !BuildConfig.DEMAND_API_BASE_URL.isBlank() ->
                ProviderConnectionStatus.ERROR
            else -> ProviderConnectionStatus.CONNECTED
        }
        return ProviderStatus(
            name = displayName,
            sourceType = SourceType.OFFICIAL_API,
            status = status,
            lastUpdatedAtEpochMs = lastUpdated,
            supportedCitiesHint = "Любой город (встроенный движок + remote backend)",
            termsOfUse = "Официальный API приложения YaRaDaR. Не данные Яндекс Про. " +
                if (usingRemote) "Режим: remote backend." else "Режим: embedded engine (offline).",
            isDemo = false
        )
    }

    override suspend fun isRealTimeDataAvailable(cityId: String): Boolean =
        cityRegistry.containsKey(cityId) || zoneCache[cityId].orEmpty().isNotEmpty()
}
