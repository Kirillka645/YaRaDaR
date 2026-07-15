package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.FareEstimate
import com.radar.coefficients.domain.model.ForecastTrend
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.MoneyRange
import com.radar.coefficients.domain.model.RadarSortMode
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.ZoneBenefitScore
import com.radar.coefficients.domain.util.GeoMath
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Выгода с учётом:
 * — доп. дохода от кэфа
 * — числа заказов в районе (вероятность взять заказ)
 * — прогноза (рост/падение кэфа к моменту прибытия)
 * — стоимости доезда
 */
object BenefitCalculator {

    data class Input(
        val zone: DemandZone,
        val driverLocation: GeoPoint,
        val settings: UserSettings,
        val fareEstimate: FareEstimate? = null,
        val fareWithoutCoefficient: FareEstimate? = null,
        val travelTimeMinutes: Int? = null,
        val travelTimeWithTrafficMinutes: Int? = null,
        val tollCost: Double = 0.0,
        val knownCommissionRate: Double? = null,
        val knownCommissionFixed: Double? = null
    )

    fun score(input: Input): ZoneBenefitScore {
        val zone = input.zone
        val distanceKm = GeoMath.distanceKm(input.driverLocation, zone.center)
        val travelMin = input.travelTimeMinutes
            ?: GeoMath.estimateTravelMinutes(distanceKm)
        val trafficMin = input.travelTimeWithTrafficMinutes
        val effectiveTravelMin = trafficMin ?: travelMin

        val fuelCost = distanceKm * input.settings.fuelCostPerKm
        val timeCost = effectiveTravelMin * input.settings.timeCostPerMinute
        val travelCost = fuelCost + timeCost + input.tollCost

        // Кэф, который «застанем» по прибытии (прогноз на горизонт ≈ времени в пути)
        val arrivalCoef = projectedCoefOnArrival(zone, effectiveTravelMin)
        val coefRatio = (arrivalCoef / zone.coefficient.coerceAtLeast(1.0)).coerceIn(0.7, 1.35)

        val orderPickupChance = orderPickupProbability(zone, effectiveTravelMin)

        val rawExtra = when {
            input.fareEstimate != null && input.fareWithoutCoefficient != null -> {
                val withCoef = input.fareEstimate.range.midpoint()
                val without = input.fareWithoutCoefficient.range.midpoint()
                max(0.0, withCoef - without)
            }
            else -> zone.extraIncome * survivalFactor(zone)
        }

        // Корректируем: прогнозный кэф × шанс взять заказ
        val expectedGrossExtra = rawExtra * coefRatio * orderPickupChance

        val commissionKnown = input.knownCommissionRate != null || input.knownCommissionFixed != null
        val commissionAmount = when {
            input.knownCommissionFixed != null -> input.knownCommissionFixed
            input.knownCommissionRate != null -> expectedGrossExtra * input.knownCommissionRate
            else -> null
        }

        val afterCommission = expectedGrossExtra - (commissionAmount ?: 0.0)
        val expectedNet = afterCommission - travelCost

        val bearing = GeoMath.bearingDegrees(input.driverLocation, zone.center)
        val breakdown = buildList {
            add("Доп. доход (база): ${fmt(rawExtra)}")
            add("Кэф сейчас ×${fmt(zone.coefficient)} → к прибытию ~×${fmt(arrivalCoef)}")
            zone.orderStats?.let {
                add("Заказов: ~${it.ordersLastHour}/ч · сегодня ~${it.ordersToday}")
                add("Шанс взять заказ (ориент.): ${(orderPickupChance * 100).roundToInt()}%")
            }
            zone.forecast?.let {
                add("Прогноз: ${it.summaryRu}")
            }
            add("Топливо (${fmt(distanceKm)} км): −${fmt(fuelCost)}")
            add("Время ($effectiveTravelMin мин): −${fmt(timeCost)}")
            if (input.tollCost > 0) add("Платные дороги: −${fmt(input.tollCost)}")
            if (commissionKnown) add("Комиссия: −${fmt(commissionAmount ?: 0.0)}")
            else add("Комиссия не учтена: данные отсутствуют")
            add("Выгода ≈ ${fmt(expectedNet)} (прогноз + заказы − доезд)")
            add("Модель YaRaDaR, не гарантия. Кэф может измениться.")
        }

        val conf = zone.confidence *
            if (zone.isStale()) 0.6 else 1.0 *
            (zone.forecast?.confidence ?: 0.75)

        return ZoneBenefitScore(
            zone = zone,
            rank = 0,
            distanceKm = distanceKm,
            travelTimeMinutes = travelMin,
            travelTimeWithTrafficMinutes = trafficMin,
            fuelCost = fuelCost,
            timeCost = timeCost,
            expectedGrossExtra = expectedGrossExtra,
            expectedNetBenefit = expectedNet,
            estimatedFareRange = input.fareEstimate?.range,
            fareWithoutCoefficientRange = input.fareWithoutCoefficient?.range,
            directionBearingDegrees = bearing,
            directionLabelRu = GeoMath.directionLabelRu(bearing),
            confidence = conf.coerceIn(0.2, 1.0),
            commissionKnown = commissionKnown,
            commissionAmount = commissionAmount,
            calculationBreakdown = breakdown
        )
    }

    fun rank(
        zones: List<DemandZone>,
        driverLocation: GeoPoint,
        settings: UserSettings,
        sortMode: RadarSortMode,
        fareByZoneId: Map<String, Pair<FareEstimate?, FareEstimate?>> = emptyMap()
    ): List<ZoneBenefitScore> {
        val scored = zones.map { zone ->
            val fares = fareByZoneId[zone.id]
            score(
                Input(
                    zone = zone,
                    driverLocation = driverLocation,
                    settings = settings,
                    fareEstimate = fares?.first,
                    fareWithoutCoefficient = fares?.second
                )
            )
        }
        val sorted = when (sortMode) {
            RadarSortMode.MAX_BENEFIT -> scored.sortedByDescending { it.expectedNetBenefit }
            RadarSortMode.HIGHEST_COEFFICIENT -> scored.sortedByDescending { it.zone.coefficient }
            RadarSortMode.NEAREST -> scored.sortedBy { it.distanceKm }
            RadarSortMode.FORECAST_IGNITE -> scored.sortedWith(
                compareByDescending<ZoneBenefitScore> {
                    it.zone.forecast?.igniteProbability30Min ?: 0.0
                }.thenByDescending { it.zone.heatScore }
                    .thenBy { it.distanceKm }
            )
            RadarSortMode.MOST_ORDERS -> scored.sortedByDescending {
                it.zone.orderStats?.ordersLastHour ?: 0
            }
        }
        return sorted.mapIndexed { index, item -> item.copy(rank = index + 1) }
    }

    /** Кэф, который ожидаем к моменту доезда. */
    fun projectedCoefOnArrival(zone: DemandZone, travelMinutes: Int): Double {
        val f = zone.forecast ?: return zone.coefficient
        return when {
            travelMinutes <= 12 -> f.coefficientIn15Min
            travelMinutes <= 25 -> f.coefficientIn30Min
            else -> f.coefficientIn60Min
        }
    }

    /**
     * Чем больше заказов и выше кэф, тем выше шанс «зацепиться».
     * Падающий прогноз и долгий доезд снижают.
     */
    fun orderPickupProbability(zone: DemandZone, travelMinutes: Int): Double {
        val orders = zone.orderStats
        val density = when {
            orders == null -> 0.55
            orders.ordersLastHour >= 40 -> 0.85
            orders.ordersLastHour >= 25 -> 0.72
            orders.ordersLastHour >= 12 -> 0.6
            else -> 0.45
        }
        val travelPenalty = when {
            travelMinutes <= 8 -> 1.0
            travelMinutes <= 15 -> 0.92
            travelMinutes <= 25 -> 0.8
            else -> 0.65
        }
        val trendFactor = when (zone.forecast?.trend) {
            ForecastTrend.RISING -> 1.08
            ForecastTrend.FALLING -> 0.82
            ForecastTrend.STABLE -> 1.0
            null -> 1.0
        }
        return (density * travelPenalty * trendFactor).coerceIn(0.25, 0.95)
    }

    fun moneyRangeFromBase(base: Double, currencyCode: String, spread: Double = 0.1): MoneyRange {
        val min = (base * (1 - spread)).roundToInt().toDouble()
        val max = (base * (1 + spread)).roundToInt().toDouble()
        return MoneyRange(min, max, currencyCode)
    }

    private fun survivalFactor(zone: DemandZone): Double {
        val p = zone.survivalProbability ?: defaultSurvival(zone.coefficient)
        return p.coerceIn(0.2, 1.0)
    }

    private fun defaultSurvival(coefficient: Double): Double = when {
        coefficient >= 2.0 -> 0.55
        coefficient >= 1.5 -> 0.7
        coefficient >= 1.2 -> 0.82
        else -> 0.9
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
}
