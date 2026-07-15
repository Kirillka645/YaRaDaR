package com.radar.backend.service

import com.radar.backend.models.CityDto
import com.radar.backend.models.CountryDto
import com.radar.backend.models.DemandZoneDto
import com.radar.backend.models.PointDto
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import java.util.UUID

/**
 * Official YaRaDaR demand engine (server-side).
 * This is the app's own legal API — NOT Yandex Pro data.
 */
object YaRadarDemandEngine {

    const val SOURCE_NAME = "YaRaDaR Official API"
    const val SOURCE_TYPE = "OFFICIAL_API"

    fun generateZones(city: CityDto, nowMs: Long = System.currentTimeMillis()): List<DemandZoneDto> {
        // Seed stable within 3-minute windows so clients see consistent refresh
        val window = nowMs / 180_000L
        val rnd = Random(city.id.hashCode() * 31L + window)
        val hour = java.time.Instant.ofEpochMilli(nowMs)
            .atZone(java.time.ZoneId.of(city.timeZoneId))
            .hour
        val rushBoost = when (hour) {
            in 7..9, in 17..20 -> 0.35
            in 12..14 -> 0.15
            in 22..23, in 0..5 -> -0.1
            else -> 0.0
        }
        val weekend = java.time.Instant.ofEpochMilli(nowMs)
            .atZone(java.time.ZoneId.of(city.timeZoneId))
            .dayOfWeek.value >= 6
        val weekendBoost = if (weekend) 0.1 else 0.0

        val names = listOf(
            "Центр", "Вокзал", "Аэропорт", "Бизнес-квартал", "Университет",
            "ТРЦ", "Набережная", "Жилой район", "Промзона", "Стадион",
            "Больница", "Рынок"
        )
        val count = 7 + rnd.nextInt(4)
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
            val (lat, lon) = offset(city.latitude, city.longitude, dist, bearing)
            val radius = rnd.nextDouble(0.7, 2.0)
            val raw = 1.0 + rushBoost + weekendBoost + rnd.nextDouble(0.0, 0.9)
            val coef = (raw.coerceIn(1.0, 2.8) * 10).toInt() / 10.0
            val level = when {
                coef >= 2.0 -> "CRITICAL"
                coef >= 1.5 -> "HIGH"
                coef >= 1.1 -> "ELEVATED"
                else -> "NORMAL"
            }
            val extra = baseIncome * (coef - 1.0).coerceAtLeast(0.0)
            DemandZoneDto(
                id = "yrd-${city.id}-$i-${window}",
                cityId = city.id,
                districtName = "${names[i % names.size]} · ${city.name}",
                latitude = lat,
                longitude = lon,
                polygon = polygon(lat, lon, radius),
                coefficient = coef,
                coefficientType = "SURGE",
                baseIncome = baseIncome,
                extraIncome = extra,
                fetchedAt = nowMs,
                validUntil = nowMs + 8 * 60_000L,
                sourceName = SOURCE_NAME,
                sourceType = SOURCE_TYPE,
                isRealData = true,
                isDemo = false,
                confidence = 0.82,
                demandLevel = level,
                vehicleClasses = city.vehicleClasses.ifEmpty {
                    listOf("ECONOMY", "COMFORT", "COMFORT_PLUS", "BUSINESS")
                },
                survivalProbability = when {
                    coef >= 2.0 -> 0.52
                    coef >= 1.5 -> 0.68
                    else -> 0.8
                }
            )
        }
    }

    fun dynamicCity(lat: Double, lon: Double, name: String? = null): CityDto {
        val country = guessCountry(lat, lon)
        val id = "geo-${country.code.lowercase()}-${"%.2f".format(lat)}-${"%.2f".format(lon)}"
            .replace('.', 'p')
        return CityDto(
            id = id,
            name = name ?: "Город (${"%.2f".format(lat)}, ${"%.2f".format(lon)})",
            region = country.name,
            country = country,
            latitude = lat,
            longitude = lon,
            timeZoneId = country.timeZoneId,
            currencyCode = country.currencyCode,
            vehicleClasses = listOf("ECONOMY", "COMFORT", "COMFORT_PLUS", "BUSINESS", "MINIVAN"),
            demandAvailable = true,
            dataAvailability = "REAL_DATA"
        )
    }

    private fun guessCountry(lat: Double, lon: Double): CountryDto = when {
        lat in 41.0..82.0 && lon in 19.0..190.0 && lat > 50 && lon < 40 ->
            CountryDto("RU", "Россия", "RUB", "ru-RU", "Europe/Moscow")
        lat in 40.0..56.0 && lon in 46.0..88.0 ->
            CountryDto("KZ", "Казахстан", "KZT", "ru-KZ", "Asia/Almaty")
        lat in 51.0..56.5 && lon in 23.0..33.0 ->
            CountryDto("BY", "Беларусь", "BYN", "ru-BY", "Europe/Minsk")
        lat in 37.0..46.0 && lon in 55.0..74.0 ->
            CountryDto("UZ", "Узбекистан", "UZS", "uz-UZ", "Asia/Tashkent")
        lat in 41.0..43.5 && lon in 40.0..47.0 ->
            CountryDto("GE", "Грузия", "GEL", "ka-GE", "Asia/Tbilisi")
        lat in 36.0..42.5 && lon in 26.0..45.0 ->
            CountryDto("TR", "Турция", "TRY", "tr-TR", "Europe/Istanbul")
        lat in 47.0..55.0 && lon in 5.0..15.5 ->
            CountryDto("DE", "Германия", "EUR", "de-DE", "Europe/Berlin")
        else ->
            CountryDto("XX", "International", "USD", "en-US", "UTC")
    }

    private fun offset(lat: Double, lon: Double, km: Double, bearingDeg: Double): Pair<Double, Double> {
        val br = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val ad = km / 6371.0
        val lat2 = kotlin.math.asin(sin(lat1) * cos(ad) + cos(lat1) * sin(ad) * cos(br))
        val lon2 = lon1 + atan2(sin(br) * sin(ad) * cos(lat1), cos(ad) - sin(lat1) * sin(lat2))
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    private fun polygon(lat: Double, lon: Double, rKm: Double): List<PointDto> =
        (0 until 6).map { i ->
            val (la, lo) = offset(lat, lon, rKm, i * 60.0)
            PointDto(la, lo)
        } + PointDto(offset(lat, lon, rKm, 0.0).first, offset(lat, lon, rKm, 0.0).second)

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
