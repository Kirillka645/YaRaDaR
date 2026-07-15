package com.radar.coefficients.data.repository

import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.RouteEstimate
import com.radar.coefficients.domain.provider.RouteProvider
import com.radar.coefficients.domain.repository.RouteRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepositoryImpl @Inject constructor(
    private val routeProvider: RouteProvider
) : RouteRepository {
    override suspend fun buildRoute(origin: GeoPoint, destination: GeoPoint): Result<RouteEstimate> =
        runCatching { routeProvider.buildRoute(origin, destination) }

    override suspend fun estimateToZone(origin: GeoPoint, zoneCenter: GeoPoint): Result<RouteEstimate> =
        buildRoute(origin, zoneCenter)
}
