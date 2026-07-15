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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Picks the best legal source. Never mixes real and demo without labels
 * (zones keep their own isDemo / isRealData flags).
 * Priority: Official → Licensed → Community → Demo (only if demo allowed).
 */
@Singleton
class CompositeDemandCoefficientProvider @Inject constructor(
    private val official: OfficialDemandCoefficientProvider,
    private val licensed: LicensedDemandCoefficientProvider,
    private val community: CommunityDemandProvider,
    private val demo: DemoDemandCoefficientProvider
) : DemandCoefficientProvider {

    override val providerId: String = "composite"
    override val displayName: String = "Составной поставщик"

    @Volatile
    var allowDemoFallback: Boolean = true

    override suspend fun getSupportedCountries(): List<Country> {
        val list = mutableListOf<Country>()
        list += official.getSupportedCountries()
        list += licensed.getSupportedCountries()
        list += demo.getSupportedCountries()
        return list.distinctBy { it.code }
    }

    override suspend fun getSupportedCities(countryCode: String): List<City> {
        val list = mutableListOf<City>()
        list += official.getSupportedCities(countryCode)
        list += licensed.getSupportedCities(countryCode)
        list += demo.getSupportedCities(countryCode)
        return list.distinctBy { it.id }
    }

    override suspend fun detectCity(latitude: Double, longitude: Double): City? {
        official.detectCity(latitude, longitude)?.let { return it }
        licensed.detectCity(latitude, longitude)?.let { return it }
        return demo.detectCity(latitude, longitude)
    }

    override suspend fun getDemandZones(cityId: String, mapBounds: GeoBounds?): List<DemandZone> {
        // YaRaDaR Official first (remote or embedded engine)
        val officialZones = official.getDemandZones(cityId, mapBounds)
        if (officialZones.isNotEmpty()) {
            return officialZones
        }
        val licensedZones = licensed.getDemandZones(cityId, mapBounds)
        if (licensedZones.isNotEmpty()) {
            return licensedZones
        }
        val communityZones = community.getDemandZones(cityId, mapBounds)
        if (communityZones.isNotEmpty() && !allowDemoFallback) {
            return communityZones
        }
        if (allowDemoFallback) {
            val demoZones = demo.getDemandZones(cityId, mapBounds)
            return (communityZones + demoZones).distinctBy { it.id }
        }
        return communityZones
    }

    override suspend fun getZoneDetails(zoneId: String): DemandZone? =
        official.getZoneDetails(zoneId)
            ?: licensed.getZoneDetails(zoneId)
            ?: community.getZoneDetails(zoneId)
            ?: demo.getZoneDetails(zoneId)

    override fun observeDemandZones(cityId: String): Flow<List<DemandZone>> {
        return combine(
            official.observeDemandZones(cityId),
            community.observeDemandZones(cityId),
            demo.observeDemandZones(cityId)
        ) { o, c, d ->
            when {
                o.isNotEmpty() -> o
                c.isNotEmpty() && !allowDemoFallback -> c
                allowDemoFallback -> (c + d).distinctBy { it.id }
                else -> c
            }
        }
    }

    override suspend fun getLastUpdatedAt(): Long? =
        listOfNotNull(
            official.getLastUpdatedAt(),
            licensed.getLastUpdatedAt(),
            community.getLastUpdatedAt(),
            demo.getLastUpdatedAt()
        ).maxOrNull()

    override suspend fun getProviderStatus(): ProviderStatus {
        val statuses = listOf(
            official.getProviderStatus(),
            licensed.getProviderStatus(),
            community.getProviderStatus(),
            demo.getProviderStatus()
        )
        val best = statuses.firstOrNull {
            it.status == ProviderConnectionStatus.CONNECTED && !it.isDemo
        } ?: statuses.firstOrNull { it.status == ProviderConnectionStatus.CONNECTED }
            ?: statuses.first()
        return best.copy(
            name = displayName,
            sourceType = SourceType.OFFICIAL_API,
            supportedCitiesHint = "Приоритет: официальный → лицензия → сообщество → демо"
        )
    }

    override suspend fun isRealTimeDataAvailable(cityId: String): Boolean =
        official.isRealTimeDataAvailable(cityId) || licensed.isRealTimeDataAvailable(cityId)

    suspend fun allStatuses(): List<ProviderStatus> = listOf(
        official.getProviderStatus(),
        licensed.getProviderStatus(),
        community.getProviderStatus(),
        demo.getProviderStatus()
    )
}
