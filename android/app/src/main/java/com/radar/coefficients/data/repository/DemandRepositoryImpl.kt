package com.radar.coefficients.data.repository

import com.radar.coefficients.data.local.dao.DemandZoneDao
import com.radar.coefficients.data.mapper.toDomain
import com.radar.coefficients.data.mapper.toEntity
import com.radar.coefficients.data.provider.CompositeDemandCoefficientProvider
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoBounds
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.repository.DemandRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemandRepositoryImpl @Inject constructor(
    private val composite: CompositeDemandCoefficientProvider,
    private val zoneDao: DemandZoneDao
) : DemandRepository {

    override suspend fun refreshZones(cityId: String, bounds: GeoBounds?): Result<List<DemandZone>> =
        runCatching {
            zoneDao.clearExpired(System.currentTimeMillis())
            val zones = composite.getDemandZones(cityId, bounds)
            zoneDao.clearCity(cityId)
            zoneDao.upsertAll(zones.map { it.toEntity() })
            zones
        }

    override fun observeZones(cityId: String): Flow<List<DemandZone>> =
        zoneDao.observeByCity(cityId).map { list -> list.map { it.toDomain() } }

    override suspend fun getCachedZones(cityId: String): List<DemandZone> =
        zoneDao.getByCity(cityId).map { it.toDomain() }

    override suspend fun getZone(zoneId: String): DemandZone? =
        zoneDao.getById(zoneId)?.toDomain() ?: composite.getZoneDetails(zoneId)

    override suspend fun getLastUpdatedAt(): Long? =
        zoneDao.lastFetched() ?: composite.getLastUpdatedAt()

    override suspend fun getActiveProvidersStatus(): List<ProviderStatus> =
        composite.allStatuses()

    override suspend fun isRealTimeAvailable(cityId: String): Boolean =
        composite.isRealTimeDataAvailable(cityId)
}
