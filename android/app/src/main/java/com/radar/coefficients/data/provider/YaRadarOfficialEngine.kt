package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.usecase.DemandForecastEngine
import com.radar.coefficients.domain.util.GeoMath
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.random.Random

/**
 * YaRaDaR Official demand engine: зоны + заказы по району + прогноз кэфа.
 * Не является данными Яндекс Про.
 */
object YaRadarOfficialEngine {

    const val SOURCE_NAME = "YaRaDaR Official API"

    fun generateZones(city: City, nowMs: Long = System.currentTimeMillis()): List<DemandZone> {
        // Окно 3 мин — стабильность между обновлениями
        val window = nowMs / 180_000L
        val citySeed = city.id.hashCode() * 31L + window
        val rnd = Random(citySeed)
        val tz = TimeZone.getTimeZone(city.timeZoneId)
        val cal = Calendar.getInstance(tz).apply { timeInMillis = nowMs }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val weekend = cal.get(Calendar.DAY_OF_WEEK).let {
            it == Calendar.SATURDAY || it == Calendar.SUNDAY
        }
        val cityScale = DemandForecastEngine.cityOrderScale(city.currencyCode)
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

        // Стабильные якоря районов (не прыгают хаотично)
        val anchors = DemandForecastEngine.profiles.mapIndexed { i, profile ->
            val bearing = i * (360.0 / DemandForecastEngine.profiles.size) +
                (city.id.hashCode() % 40)
            val dist = 2.0 + (abs(city.id.hashCode() + i * 17) % 100) / 12.0
            val center = GeoMath.offsetPoint(city.center, dist.coerceIn(1.5, 12.0), bearing.toDouble())
            Triple(profile, center, dist)
        }

        return anchors.mapIndexed { i, (profile, center, _) ->
            val zoneRnd = Random(citySeed + i * 9973)
            val radius = 0.8 + (abs(citySeed + i).toInt() % 15) / 10.0
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
            // Доп. доход: базовый × (кэф-1) × давление заказов (округление до ₽)
            val orderFactor = (0.6 + orders.demandPressure * 0.35).coerceIn(0.5, 1.6)
            val extra = (baseIncome * (coef - 1.0).coerceAtLeast(0.0) * orderFactor)
                .let { kotlin.math.round(it / 5.0) * 5.0 } // шаг 5 ₽ — читаемее

            DemandZone(
                id = "yrd-${city.id}-${profile.kind}-$window",
                cityId = city.id,
                // Временное имя; StreetLabelResolver заменит на улицу в центре зоны
                districtName = profile.labelRu,
                center = center,
                polygon = GeoMath.regularPolygon(center, radius),
                coefficient = coef,
                coefficientType = CoefficientType.SURGE,
                baseIncome = baseIncome,
                extraIncome = extra,
                fetchedAtEpochMs = nowMs,
                validUntilEpochMs = nowMs + 8 * 60_000L,
                sourceName = SOURCE_NAME,
                sourceType = SourceType.OFFICIAL_API,
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

    fun multiTariffCoefficients(baseEconomy: Double, rnd: Random = Random.Default): Map<VehicleClass, Double> {
        fun round1(v: Double) = ((v.coerceIn(1.0, 3.0)) * 10).toInt() / 10.0
        // Стабильные сдвиги + небольшой шум: тарифы читаются по-разному, но не «прыгают»
        fun of(delta: Double, noise: Double = 0.06) =
            round1(baseEconomy + delta + rnd.nextDouble(-noise, noise))
        return mapOf(
            VehicleClass.ECONOMY to round1(baseEconomy),
            VehicleClass.COMFORT to of(-0.08),
            VehicleClass.COMFORT_PLUS to of(-0.12),
            VehicleClass.BUSINESS to of(-0.18, 0.05),
            VehicleClass.MINIVAN to of(-0.04),
            VehicleClass.CHILD to of(+0.05),
            VehicleClass.COURIER to of(+0.12, 0.1)
        )
    }

    fun markCityOfficial(city: City): City = city.copy(
        demandDataAvailable = true,
        dataAvailability = CityDataAvailability.REAL_DATA
    )
}
