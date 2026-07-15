package com.radar.coefficients.domain.provider

import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.RouteEstimate

interface RouteProvider {
    val providerId: String

    suspend fun buildRoute(origin: GeoPoint, destination: GeoPoint): RouteEstimate
    suspend fun getDistance(origin: GeoPoint, destination: GeoPoint): Double
    suspend fun getDuration(origin: GeoPoint, destination: GeoPoint): Int
    suspend fun getDurationWithTraffic(origin: GeoPoint, destination: GeoPoint): Int?
}
