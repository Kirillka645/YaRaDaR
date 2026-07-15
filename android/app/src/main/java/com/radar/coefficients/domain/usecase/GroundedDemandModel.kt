package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.DistrictOrderStats
import com.radar.coefficients.domain.model.ForecastTrend
import com.radar.coefficients.domain.model.ZoneForecast
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Обоснованный кэф из **открытых сигналов**:
 * — час / день недели (типичный спрос на такси)
 * — погода (Open-Meteo)
 * — плотность POI OSM (вокзал, ТРЦ, рестораны…)
 *
 * Не использует API Яндекса. Даёт воспроизводимый (почти без random) результат.
 */
object GroundedDemandModel {

    data class Input(
        val hour: Int,
        val weekend: Boolean,
        val weatherBoost: Double,      // 0..0.7
        val poiScore: Double,          // 0..1
        val profile: DemandForecastEngine.DistrictProfile,
        val cityScale: Double = 1.0
    )

    data class Result(
        val coefficient: Double,
        val orders: DistrictOrderStats,
        val forecast: ZoneForecast,
        val heatScore: Int,
        val breakdownRu: String
    )

    fun compute(input: Input): Result {
        val hourF = DemandForecastEngine.hourFactor(input.hour, input.profile, input.weekend)
        // Исследования такси: пики 7–10 и 17–21 в будни
        val rush = rushFactor(input.hour, input.weekend)
        val weather = input.weatherBoost.coerceIn(0.0, 0.7)
        val poi = input.poiScore.coerceIn(0.0, 1.0)
        val sens = input.profile.surgeSensitivity

        // База спроса 0..~2.2 → кэф 1.0..~2.6
        val demandIndex =
            0.15 * hourF +
                0.35 * rush +
                0.55 * poi * sens +
                weather * 0.9 +
                if (input.weekend && input.hour in 22..23) 0.12 else 0.0 +
                if (input.weekend && input.hour in 0..3) 0.18 else 0.0

        val coef = round1(
            (1.0 + demandIndex * 0.85).coerceIn(1.0, 2.8)
        )

        val baseOrders = input.profile.baseOrdersPerHour * input.cityScale
        val perHour = max(1.0, baseOrders * (0.4 + hourF * 0.5 + poi * 0.8 + weather * 0.6 + rush * 0.4))
        val lastHour = perHour.roundToInt().coerceAtLeast(1)
        val last15 = max(0, (lastHour / 4.0 * (0.75 + poi * 0.3)).roundToInt())
        var day = 0.0
        for (h in 0..input.hour) {
            day += baseOrders * DemandForecastEngine.hourFactor(h, input.profile, input.weekend) * 0.9
        }
        val pressure = (lastHour / (baseOrders * 1.15)).coerceIn(0.2, 2.8)
        val orders = DistrictOrderStats(
            ordersLast15Min = last15,
            ordersLastHour = lastHour,
            ordersToday = max(lastHour, day.roundToInt()),
            avgOrdersPerHour = perHour * 0.92,
            peakHourLocal = if (input.weekend) 22 else 18,
            demandPressure = pressure
        )

        val forecast = buildForecast(coef, input)
        val heat = DemandForecastEngine.heatScore(coef, orders, forecast)
        val breakdown = buildString {
            append("час×${"%.2f".format(hourF)}")
            append(" · пик×${"%.2f".format(rush)}")
            append(" · OSM×${"%.2f".format(poi)}")
            append(" · погода+${"%.2f".format(weather)}")
            append(" → ×${"%.1f".format(coef)}")
        }
        return Result(coef, orders, forecast, heat, breakdown)
    }

    /** 0..1 — «час пик» (прокси пробок/спроса без платных traffic API). */
    fun rushFactor(hour: Int, weekend: Boolean): Double {
        if (weekend) {
            return when (hour) {
                in 11..15 -> 0.45
                in 16..20 -> 0.55
                in 21..23 -> 0.7
                in 0..2 -> 0.5
                else -> 0.2
            }
        }
        return when (hour) {
            in 7..9 -> 0.85
            in 10..11 -> 0.4
            in 12..15 -> 0.35
            in 17..20 -> 0.95
            in 21..22 -> 0.55
            in 23..23, in 0..5 -> 0.25
            else -> 0.3
        }
    }

    private fun buildForecast(current: Double, input: Input): ZoneForecast {
        fun at(minutes: Int): Double {
            val h = (input.hour + minutes / 60) % 24
            val future = compute(
                input.copy(hour = h)
            ).coefficient
            val a = when {
                minutes <= 15 -> 0.3
                minutes <= 30 -> 0.5
                else -> 0.7
            }
            return round1(current * (1 - a) + future * a)
        }
        val c15 = at(15)
        val c30 = at(30)
        val c60 = at(60)
        val delta = c30 - current
        val trend = when {
            delta >= 0.12 -> ForecastTrend.RISING
            delta <= -0.12 -> ForecastTrend.FALLING
            else -> ForecastTrend.STABLE
        }
        val ignite = 1.5
        val igniteProb = when {
            c30 >= 2.0 -> 0.82
            c30 >= 1.7 -> 0.68
            c30 >= ignite -> 0.55
            current >= ignite -> 0.4
            else -> 0.15
        }
        val mins = when {
            current >= ignite -> 0
            c15 >= ignite -> 12
            c30 >= ignite -> 25
            c60 >= ignite -> 45
            else -> null
        }
        val conf = (0.62 + input.poiScore * 0.15 + input.weatherBoost * 0.1).coerceIn(0.5, 0.9)
        val summary = buildString {
            when (trend) {
                ForecastTrend.RISING -> append("Рост спроса (модель)")
                ForecastTrend.FALLING -> append("Спад спроса (модель)")
                ForecastTrend.STABLE -> append("Спрос стабилен (модель)")
            }
            append(" · 30м ×${"%.1f".format(c30)}")
            mins?.let { if (it > 0) append(" · пик ~через $it мин") }
            append(" · Open-Meteo+OSM")
        }
        return ZoneForecast(
            coefficientIn15Min = c15,
            coefficientIn30Min = c30,
            coefficientIn60Min = c60,
            trend = trend,
            igniteProbability30Min = igniteProb,
            minutesToIgnite = mins,
            confidence = conf,
            summaryRu = summary
        )
    }

    private fun round1(v: Double): Double = ((v * 10.0).roundToInt() / 10.0)
}
