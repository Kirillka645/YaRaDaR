package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.DistrictOrderStats
import com.radar.coefficients.domain.model.ForecastTrend
import com.radar.coefficients.domain.model.ZoneForecast
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Модель прогноза кэфа и заказов по типу района + времени суток.
 * Это эвристика YaRaDaR, не факт из Яндекс Про.
 */
object DemandForecastEngine {

    data class DistrictProfile(
        val kind: String,
        val labelRu: String,
        /** Пиковые часы (локальные) */
        val peakHours: IntRange,
        val eveningPeak: IntRange? = null,
        val baseOrdersPerHour: Double,
        val surgeSensitivity: Double,
        val weekendBoost: Double
    )

    val profiles: List<DistrictProfile> = listOf(
        DistrictProfile("center", "Центр", 8..10, 17..21, 42.0, 1.15, 0.15),
        DistrictProfile("station", "Вокзал", 6..10, 16..22, 55.0, 1.25, 0.05),
        DistrictProfile("airport", "Аэропорт", 5..9, 18..23, 48.0, 1.35, 0.1),
        DistrictProfile("business", "Бизнес-квартал", 7..10, 17..20, 38.0, 1.1, -0.2),
        DistrictProfile("university", "Университет", 8..11, 17..22, 28.0, 1.05, 0.25),
        DistrictProfile("mall", "ТРЦ", 11..14, 17..22, 45.0, 1.2, 0.35),
        DistrictProfile("embankment", "Набережная", 12..16, 18..23, 22.0, 0.95, 0.4),
        DistrictProfile("residential", "Жилой район", 7..9, 17..20, 32.0, 0.9, 0.05),
        DistrictProfile("industrial", "Промзона", 6..9, 16..19, 18.0, 0.85, -0.15),
        DistrictProfile("stadium", "Стадион", 17..23, null, 25.0, 1.4, 0.5),
        DistrictProfile("hospital", "Больница", 8..12, 14..18, 20.0, 0.8, 0.0),
        DistrictProfile("market", "Рынок", 6..12, 15..18, 35.0, 1.0, 0.2)
    )

    fun profileForIndex(i: Int): DistrictProfile = profiles[i % profiles.size]

    fun hourFactor(hour: Int, profile: DistrictProfile, weekend: Boolean): Double {
        var f = 0.55
        if (hour in profile.peakHours) f += 0.45
        profile.eveningPeak?.let { if (hour in it) f += 0.4 }
        // mid-day
        if (hour in 11..15) f += 0.15
        // night drop except airport/station
        if (hour in 0..5) {
            f -= if (profile.kind == "airport" || profile.kind == "station") 0.05 else 0.35
        }
        if (weekend) f += profile.weekendBoost
        return f.coerceIn(0.2, 1.6)
    }

    fun estimateOrders(
        profile: DistrictProfile,
        hour: Int,
        weekend: Boolean,
        rnd: Random,
        cityScale: Double = 1.0
    ): DistrictOrderStats {
        val hf = hourFactor(hour, profile, weekend)
        val perHour = profile.baseOrdersPerHour * hf * cityScale * (0.85 + rnd.nextDouble(0.0, 0.3))
        val lastHour = max(1, perHour.roundToInt())
        val last15 = max(0, (lastHour / 4.0 * (0.7 + rnd.nextDouble(0.0, 0.6))).roundToInt())
        // today approx from cumulative hour factors
        var daySum = 0.0
        for (h in 0..hour) {
            daySum += profile.baseOrdersPerHour * hourFactor(h, profile, weekend) * cityScale * 0.95
        }
        val today = max(lastHour, daySum.roundToInt())
        val peak = listOf(
            profile.peakHours.first,
            profile.eveningPeak?.first ?: profile.peakHours.last
        ).maxOrNull() ?: 18
        val pressure = (lastHour / (profile.baseOrdersPerHour * 1.2)).coerceIn(0.2, 2.5)
        return DistrictOrderStats(
            ordersLast15Min = last15,
            ordersLastHour = lastHour,
            ordersToday = today,
            avgOrdersPerHour = (perHour * 0.9).coerceAtLeast(1.0),
            peakHourLocal = peak,
            demandPressure = pressure
        )
    }

    /**
     * Текущий кэф из давления заказов + «шум» района.
     */
    fun currentCoefficient(
        profile: DistrictProfile,
        hour: Int,
        weekend: Boolean,
        orders: DistrictOrderStats,
        rnd: Random
    ): Double {
        val base = 1.0 +
            (orders.demandPressure - 0.8) * 0.55 * profile.surgeSensitivity +
            hourFactor(hour, profile, weekend) * 0.25 * profile.surgeSensitivity
        val noise = rnd.nextDouble(-0.12, 0.18)
        return round1((base + noise).coerceIn(1.0, 2.9))
    }

    fun forecast(
        currentCoef: Double,
        profile: DistrictProfile,
        hour: Int,
        weekend: Boolean,
        orders: DistrictOrderStats,
        rnd: Random
    ): ZoneForecast {
        fun future(minutes: Int): Double {
            val h = (hour + (minutes / 60.0)).let { x ->
                val i = x.toInt() % 24
                if (i < 0) i + 24 else i
            }
            val targetOrders = estimateOrders(profile, h, weekend, rnd, cityScale = 1.0)
            val target = currentCoefficient(profile, h, weekend, targetOrders, rnd)
            // плавный переход к целевому
            val alpha = when {
                minutes <= 15 -> 0.35
                minutes <= 30 -> 0.55
                else -> 0.75
            }
            return round1(currentCoef * (1 - alpha) + target * alpha)
        }

        val c15 = future(15)
        val c30 = future(30)
        val c60 = future(60)
        val delta = c30 - currentCoef
        val trend = when {
            delta >= 0.15 -> ForecastTrend.RISING
            delta <= -0.15 -> ForecastTrend.FALLING
            else -> ForecastTrend.STABLE
        }
        val igniteThreshold = 1.5
        val igniteProb = when {
            c30 >= 2.0 -> 0.85
            c30 >= 1.7 -> 0.72
            c30 >= igniteThreshold -> 0.6
            c60 >= igniteThreshold && trend == ForecastTrend.RISING -> 0.48
            currentCoef >= igniteThreshold -> 0.4
            else -> 0.18
        }.coerceIn(0.05, 0.95)

        val minutesToIgnite = when {
            currentCoef >= igniteThreshold && c15 >= igniteThreshold -> 0
            c15 >= igniteThreshold -> 10 + rnd.nextInt(6)
            c30 >= igniteThreshold -> 20 + rnd.nextInt(10)
            c60 >= igniteThreshold -> 40 + rnd.nextInt(15)
            else -> null
        }

        val conf = (0.55 + abs(orders.demandPressure - 1.0) * 0.1 +
            if (trend != ForecastTrend.STABLE) 0.08 else 0.0).coerceIn(0.4, 0.85)

        val summary = buildString {
            when (trend) {
                ForecastTrend.RISING -> append("Рост кэфа ожидается")
                ForecastTrend.FALLING -> append("Кэф может просесть")
                ForecastTrend.STABLE -> append("Кэф относительно стабилен")
            }
            append(" · 15м ×${"%.1f".format(c15)}")
            append(" · 30м ×${"%.1f".format(c30)}")
            minutesToIgnite?.let {
                if (it > 0) append(" · «загорится» через ~$it мин")
                else if (currentCoef >= igniteThreshold) append(" · уже горячо")
            }
        }

        return ZoneForecast(
            coefficientIn15Min = c15,
            coefficientIn30Min = c30,
            coefficientIn60Min = c60,
            trend = trend,
            igniteProbability30Min = igniteProb,
            minutesToIgnite = minutesToIgnite,
            confidence = conf,
            summaryRu = summary
        )
    }

    fun heatScore(
        coef: Double,
        orders: DistrictOrderStats,
        forecast: ZoneForecast
    ): Int {
        val coefPart = ((coef - 1.0) / 1.8).coerceIn(0.0, 1.0) * 45
        val orderPart = (orders.ordersLastHour / 60.0).coerceIn(0.0, 1.0) * 30
        val forecastPart = when (forecast.trend) {
            ForecastTrend.RISING -> 15.0 + forecast.igniteProbability30Min * 10
            ForecastTrend.STABLE -> 8.0
            ForecastTrend.FALLING -> 3.0
        }
        return (coefPart + orderPart + forecastPart).roundToInt().coerceIn(0, 100)
    }

    fun survivalFromForecast(coef: Double, forecast: ZoneForecast): Double {
        val base = when {
            coef >= 2.0 -> 0.48
            coef >= 1.5 -> 0.62
            coef >= 1.2 -> 0.75
            else -> 0.88
        }
        return when (forecast.trend) {
            ForecastTrend.RISING -> min(0.92, base + 0.12)
            ForecastTrend.FALLING -> max(0.28, base - 0.18)
            ForecastTrend.STABLE -> base
        }
    }

    private fun round1(v: Double): Double = (v * 10).roundToInt() / 10.0

    fun cityOrderScale(currency: String): Double = when (currency) {
        "RUB" -> 1.0
        "KZT" -> 0.9
        "BYN" -> 0.7
        else -> 0.85
    }
}
