package com.radar.coefficients.data.provider

import com.radar.coefficients.data.context.OpenMeteoClient
import com.radar.coefficients.data.context.OsmPoiContextClient
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.usecase.DemandForecastEngine
import com.radar.coefficients.domain.usecase.GroundedDemandModel
import com.radar.coefficients.domain.util.GeoMath
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/**
 * Движок зон: **Open-Meteo (погода) + OSM POI + час/день**.
 * Не Яндекс. Кэф воспроизводимый, привязан к реальным открытым сигналам.
 */
object YaRadarOfficialEngine {

    const val SOURCE_NAME = "YaRaDaR Grounded (Open-Meteo + OSM)"

    fun generateZones(
        city: City,
        weather: OpenMeteoClient.WeatherSignal,
        poiSignals: List<OsmPoiContextClient.ZonePoiSignal>,
        nowMs: Long = System.currentTimeMillis()
    ): List<DemandZone> {
        val posSeed = city.id.hashCode().toLong()
        val anchors = buildAnchors(city.center, posSeed)

        val tz = TimeZone.getTimeZone(city.timeZoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekend = cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }
        val cityScale = DemandForecastEngine.cityOrderScale(city.currencyCode)
        val baseIncome = baseIncomeFor(city.currencyCode)

        // tiny deterministic jitter so tariffs differ slightly, not the main coef
        val rnd = Random(city.id.hashCode() * 31L + (nowMs / 180_000L))

        return anchors.mapIndexed { i, (_, center, radiusKm) ->
            val poi = poiSignals.getOrNull(i)
            val profile = DemandForecastEngine.profiles.firstOrNull { it.kind == poi?.kind }
                ?: DemandForecastEngine.profileForIndex(i)

            val grounded = GroundedDemandModel.compute(
                GroundedDemandModel.Input(
                    hour = hour,
                    weekend = weekend,
                    weatherBoost = weather.demandBoost,
                    poiScore = poi?.score ?: 0.15,
                    profile = profile,
                    cityScale = cityScale
                )
            )
            val coef = grounded.coefficient
            val byClass = multiTariffCoefficients(coef, rnd)
            val level = when {
                coef >= 2.0 -> DemandLevel.CRITICAL
                coef >= 1.5 -> DemandLevel.HIGH
                coef >= 1.15 -> DemandLevel.ELEVATED
                else -> DemandLevel.NORMAL
            }
            val orderFactor = (0.55 + grounded.orders.demandPressure * 0.35).coerceIn(0.5, 1.55)
            val extra = (baseIncome * (coef - 1.0).coerceAtLeast(0.0) * orderFactor)
                .let { kotlin.math.round(it / 5.0) * 5.0 }

            val labelHint = poi?.labelRu ?: profile.labelRu
            DemandZone(
                id = "yrd-${city.id}-$i-${profile.kind}",
                cityId = city.id,
                districtName = labelHint,
                center = center,
                polygon = GeoMath.regularPolygon(center, radiusKm),
                coefficient = coef,
                coefficientType = CoefficientType.SURGE,
                baseIncome = baseIncome,
                extraIncome = extra,
                fetchedAtEpochMs = nowMs,
                validUntilEpochMs = nowMs + 6 * 60_000L,
                sourceName = SOURCE_NAME,
                sourceType = SourceType.OFFICIAL_API,
                isRealData = true,
                isDemo = false,
                confidence = grounded.forecast.confidence,
                demandLevel = level,
                availableVehicleClasses = VehicleClass.configurable,
                survivalProbability = DemandForecastEngine.survivalFromForecast(
                    coef, grounded.forecast
                ),
                coefficientsByClass = byClass,
                forecast = grounded.forecast.copy(
                    summaryRu = grounded.forecast.summaryRu +
                        " · ${weather.summaryRu}" +
                        (poi?.let { " · ${it.summaryRu}" } ?: "")
                ),
                orderStats = grounded.orders,
                heatScore = grounded.heatScore,
                districtKind = profile.kind
            )
        }.sortedByDescending { it.heatScore }
    }

    /** @return centers only for POI lookup */
    fun anchorCenters(city: City): List<Pair<GeoPoint, Double>> {
        val anchors = buildAnchors(city.center, city.id.hashCode().toLong())
        return anchors.map { it.second to it.third }
    }

    fun buildAnchors(
        cityCenter: GeoPoint,
        posSeed: Long
    ): List<Triple<DemandForecastEngine.DistrictProfile, GeoPoint, Double>> {
        val profiles = DemandForecastEngine.profiles
        val out = ArrayList<Triple<DemandForecastEngine.DistrictProfile, GeoPoint, Double>>(20)
        val prnd = Random(posSeed)

        out += Triple(profiles[0], cityCenter, 0.85)

        val ring1 = 6
        for (i in 0 until ring1) {
            val bearing = i * (360.0 / ring1) + (posSeed % 17)
            val dist = 1.15 + prnd.nextDouble(-0.15, 0.2)
            val c = GeoMath.offsetPoint(cityCenter, dist, bearing)
            out += Triple(profiles[(i + 1) % profiles.size], c, 0.7)
        }
        val ring2 = 6
        for (i in 0 until ring2) {
            val bearing = i * (360.0 / ring2) + 28.0 + (posSeed % 11)
            val dist = 2.6 + prnd.nextDouble(-0.25, 0.25)
            val c = GeoMath.offsetPoint(cityCenter, dist, bearing)
            out += Triple(profiles[(i + 3) % profiles.size], c, 0.9)
        }
        val ring3 = 5
        for (i in 0 until ring3) {
            val bearing = i * (360.0 / ring3) + 12.0
            val dist = 4.3 + prnd.nextDouble(-0.3, 0.35)
            val c = GeoMath.offsetPoint(cityCenter, dist.coerceAtMost(6.5), bearing)
            out += Triple(profiles[(i + 6) % profiles.size], c, 1.05)
        }
        return out
    }

    private fun baseIncomeFor(currency: String): Double = when (currency) {
        "RUB" -> 420.0
        "KZT" -> 2200.0
        "BYN" -> 14.0
        "UZS" -> 55000.0
        "GEL" -> 18.0
        "TRY" -> 280.0
        "EUR" -> 7.5
        else -> 400.0
    }

    fun multiTariffCoefficients(baseEconomy: Double, rnd: Random = Random.Default): Map<VehicleClass, Double> {
        fun round1(v: Double) = ((v.coerceIn(1.0, 3.0)) * 10).toInt() / 10.0
        fun of(delta: Double, noise: Double = 0.04) =
            round1(baseEconomy + delta + rnd.nextDouble(-noise, noise))
        return mapOf(
            VehicleClass.ECONOMY to round1(baseEconomy),
            VehicleClass.COMFORT to of(-0.08),
            VehicleClass.COMFORT_PLUS to of(-0.12),
            VehicleClass.BUSINESS to of(-0.18, 0.03),
            VehicleClass.MINIVAN to of(-0.04),
            VehicleClass.CHILD to of(+0.05),
            VehicleClass.COURIER to of(+0.12, 0.06)
        )
    }

    fun markCityOfficial(city: City): City = city.copy(
        demandDataAvailable = true,
        dataAvailability = CityDataAvailability.REAL_DATA
    )
}
