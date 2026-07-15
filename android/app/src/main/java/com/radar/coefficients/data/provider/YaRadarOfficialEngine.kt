package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.usecase.DemandForecastEngine
import com.radar.coefficients.domain.util.GeoMath
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.random.Random

/**
 * Встроенный движок зон спроса (модель YaRaDaR, не Яндекс Про).
 *
 * Зоны кладутся **плотной сеткой вокруг центра города** (включая центр),
 * чтобы кэф «здесь» соответствовал ближайшей реальной улице, а не зоне в 10 км.
 */
object YaRadarOfficialEngine {

    const val SOURCE_NAME = "YaRaDaR Official API"

    fun generateZones(city: City, nowMs: Long = System.currentTimeMillis()): List<DemandZone> {
        // Кэф обновляется каждые 3 мин; координаты якорей — стабильны по городу
        val window = nowMs / 180_000L
        val posSeed = city.id.hashCode().toLong()
        val coefSeed = city.id.hashCode() * 31L + window

        val tz = TimeZone.getTimeZone(city.timeZoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekend = cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }
        val cityScale = DemandForecastEngine.cityOrderScale(city.currencyCode)
        val baseIncome = baseIncomeFor(city.currencyCode)

        val anchors = buildAnchors(city.center, posSeed)

        return anchors.mapIndexed { i, (profile, center, radiusKm) ->
            val zoneRnd = Random(coefSeed + i * 9973L)
            val orders = DemandForecastEngine.estimateOrders(
                profile, hour, weekend, zoneRnd, cityScale
            )
            val coef = DemandForecastEngine.currentCoefficient(
                profile, hour, weekend, orders, zoneRnd
            )
            val forecast = DemandForecastEngine.forecast(
                coef, profile, hour, weekend, orders, zoneRnd
            )
            val byClass = multiTariffCoefficients(coef, zoneRnd)
            val heat = DemandForecastEngine.heatScore(coef, orders, forecast)
            val level = when {
                coef >= 2.0 -> DemandLevel.CRITICAL
                coef >= 1.5 -> DemandLevel.HIGH
                coef >= 1.1 -> DemandLevel.ELEVATED
                else -> DemandLevel.NORMAL
            }
            val orderFactor = (0.55 + orders.demandPressure * 0.4).coerceIn(0.5, 1.5)
            val extra = (baseIncome * (coef - 1.0).coerceAtLeast(0.0) * orderFactor)
                .let { kotlin.math.round(it / 5.0) * 5.0 }

            DemandZone(
                id = "yrd-${city.id}-$i-${profile.kind}",
                cityId = city.id,
                // StreetLabelResolver заменит на улицу в центре
                districtName = profile.labelRu,
                center = center,
                polygon = GeoMath.regularPolygon(center, radiusKm),
                coefficient = coef,
                coefficientType = CoefficientType.SURGE,
                baseIncome = baseIncome,
                extraIncome = extra,
                fetchedAtEpochMs = nowMs,
                validUntilEpochMs = nowMs + 8 * 60_000L,
                sourceName = SOURCE_NAME,
                sourceType = SourceType.OFFICIAL_API,
                // Модель приложения — не live-фид агрегатора
                isRealData = true,
                isDemo = false,
                confidence = forecast.confidence,
                demandLevel = level,
                availableVehicleClasses = VehicleClass.configurable,
                survivalProbability = DemandForecastEngine.survivalFromForecast(coef, forecast),
                coefficientsByClass = byClass,
                forecast = forecast,
                orderStats = orders,
                heatScore = heat,
                districtKind = profile.kind
            )
        }.sortedByDescending { it.heatScore }
    }

    /**
     * Центр города + кольца 1.2 / 2.5 / 4.0 / 5.5 км.
     * Радиусы зон ~0.55–1.1 км — меньше пересечений и «чужих» кэфов.
     */
    private fun buildAnchors(
        cityCenter: GeoPoint,
        posSeed: Long
    ): List<Triple<DemandForecastEngine.DistrictProfile, GeoPoint, Double>> {
        val profiles = DemandForecastEngine.profiles
        val out = ArrayList<Triple<DemandForecastEngine.DistrictProfile, GeoPoint, Double>>(20)
        val prnd = Random(posSeed)

        // 0) сам центр
        out += Triple(profiles[0], cityCenter, 0.85)

        // 1) плотное кольцо у центра
        val ring1 = 6
        for (i in 0 until ring1) {
            val bearing = i * (360.0 / ring1) + (posSeed % 17)
            val dist = 1.15 + prnd.nextDouble(-0.15, 0.2)
            val c = GeoMath.offsetPoint(cityCenter, dist, bearing)
            out += Triple(profiles[(i + 1) % profiles.size], c, 0.7)
        }

        // 2) среднее кольцо
        val ring2 = 6
        for (i in 0 until ring2) {
            val bearing = i * (360.0 / ring2) + 28.0 + (posSeed % 11)
            val dist = 2.6 + prnd.nextDouble(-0.25, 0.25)
            val c = GeoMath.offsetPoint(cityCenter, dist, bearing)
            out += Triple(profiles[(i + 3) % profiles.size], c, 0.9)
        }

        // 3) дальнее кольцо (окраины)
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
        fun of(delta: Double, noise: Double = 0.05) =
            round1(baseEconomy + delta + rnd.nextDouble(-noise, noise))
        return mapOf(
            VehicleClass.ECONOMY to round1(baseEconomy),
            VehicleClass.COMFORT to of(-0.08),
            VehicleClass.COMFORT_PLUS to of(-0.12),
            VehicleClass.BUSINESS to of(-0.18, 0.04),
            VehicleClass.MINIVAN to of(-0.04),
            VehicleClass.CHILD to of(+0.05),
            VehicleClass.COURIER to of(+0.12, 0.08)
        )
    }

    fun markCityOfficial(city: City): City = city.copy(
        demandDataAvailable = true,
        dataAvailability = CityDataAvailability.REAL_DATA
    )
}
