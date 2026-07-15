package com.radar.coefficients.domain.util

import com.radar.coefficients.domain.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_KM = 6371.0

    fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }

    fun directionLabelRu(bearing: Float): String = when (bearing) {
        in 0f..22.5f, in 337.5f..360f -> "С"
        in 22.5f..67.5f -> "СВ"
        in 67.5f..112.5f -> "В"
        in 112.5f..157.5f -> "ЮВ"
        in 157.5f..202.5f -> "Ю"
        in 202.5f..247.5f -> "ЮЗ"
        in 247.5f..292.5f -> "З"
        else -> "СЗ"
    }

    fun estimateTravelMinutes(distanceKm: Double, avgSpeedKmh: Double = 28.0): Int {
        if (distanceKm <= 0) return 0
        return ((distanceKm / avgSpeedKmh) * 60).roundToInt().coerceAtLeast(1)
    }

    fun offsetPoint(center: GeoPoint, distanceKm: Double, bearingDeg: Double): GeoPoint {
        val br = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val angDist = distanceKm / EARTH_RADIUS_KM
        val lat2 = kotlin.math.asin(
            sin(lat1) * cos(angDist) + cos(lat1) * sin(angDist) * cos(br)
        )
        val lon2 = lon1 + atan2(
            sin(br) * sin(angDist) * cos(lat1),
            cos(angDist) - sin(lat1) * sin(lat2)
        )
        return GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    fun regularPolygon(center: GeoPoint, radiusKm: Double, sides: Int = 6): List<GeoPoint> {
        return (0 until sides).map { i ->
            offsetPoint(center, radiusKm, i * (360.0 / sides))
        } + listOf(offsetPoint(center, radiusKm, 0.0))
    }
}
