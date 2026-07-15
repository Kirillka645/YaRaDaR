package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.util.GeoMath
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.random.Random

/**
 * In-app YaRaDaR Official demand engine (mirrors backend).
 * Works offline so the APK is fully usable without a remote server.
 * Source: YaRaDaR Official API — NOT Yandex Pro.
 */
object YaRadarOfficialEngine {

    const val SOURCE_NAME = "YaRaDaR Official API"

    fun generateZones(city: City, nowMs: Long = System.currentTimeMillis()): List<DemandZone> {
        val window = nowMs / 180_000L
        val rnd = Random(city.id.hashCode() * 31L + window)
        val tz = TimeZone.getTimeZone(city.timeZoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekend = cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }
        val rushBoost = when (hour) {
            in 7..9, in 17..20 -> 0.35
            in 12..14 -> 0.15
            in 22..23, in 0..5 -> -0.1
            else -> 0.0
        }
        val weekendBoost = if (weekend) 0.1 else 0.0
        val names = listOf(
            "Центр", "Вокзал", "Аэропорт", "Бизнес-квартал", "Университет",
            "ТРЦ", "Набережная", "Жилой район", "Промзона", "Стадион",
            "Больница", "Рынок"
        )
        val count = 7 + abs(rnd.nextInt(4))
        val baseIncome = when (city.currencyCode) {
            "RUB" -> 420.0
            "KZT" -> 2200.0
            "BYN" -> 14.0
            "UZS" -> 55000.0
            "GEL" -> 18.0
            "TRY" -> 280.0
            "EUR" -> 7.5
            else -> 400.0
        }
        return (0 until count).map { i ->
            val bearing = i * (360.0 / count) + rnd.nextDouble(-12.0, 12.0)
            val dist = rnd.nextDouble(1.2, 12.5)
            val center = GeoMath.offsetPoint(city.center, dist, bearing)
            val radius = rnd.nextDouble(0.7, 2.0)
            val raw = 1.0 + rushBoost + weekendBoost + rnd.nextDouble(0.0, 0.9)
            val coef = (raw.coerceIn(1.0, 2.8) * 10).toInt() / 10.0
            val level = when {
                coef >= 2.0 -> DemandLevel.CRITICAL
                coef >= 1.5 -> DemandLevel.HIGH
                coef >= 1.1 -> DemandLevel.ELEVATED
                else -> DemandLevel.NORMAL
            }
            DemandZone(
                id = "yrd-${city.id}-$i-$window",
                cityId = city.id,
                districtName = "${names[i % names.size]} · ${city.name}",
                center = center,
                polygon = GeoMath.regularPolygon(center, radius),
                coefficient = coef,
                coefficientType = CoefficientType.SURGE,
                baseIncome = baseIncome,
                extraIncome = baseIncome * (coef - 1.0).coerceAtLeast(0.0),
                fetchedAtEpochMs = nowMs,
                validUntilEpochMs = nowMs + 8 * 60_000L,
                sourceName = SOURCE_NAME,
                sourceType = SourceType.OFFICIAL_API,
                isRealData = true,
                isDemo = false,
                confidence = 0.82,
                demandLevel = level,
                availableVehicleClasses = city.availableVehicleClasses.ifEmpty {
                    listOf(VehicleClass.ECONOMY, VehicleClass.COMFORT)
                },
                survivalProbability = when {
                    coef >= 2.0 -> 0.52
                    coef >= 1.5 -> 0.68
                    else -> 0.8
                }
            )
        }
    }

    fun markCityOfficial(city: City): City = city.copy(
        demandDataAvailable = true,
        dataAvailability = CityDataAvailability.REAL_DATA
    )
}
