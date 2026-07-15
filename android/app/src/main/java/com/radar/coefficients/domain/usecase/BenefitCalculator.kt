package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.FareEstimate
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.MoneyRange
import com.radar.coefficients.domain.model.RadarSortMode
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.ZoneBenefitScore
import com.radar.coefficients.domain.util.GeoMath
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Transparent ranking:
 * Benefit = expected extra income − travel cost (fuel + time value) − known tolls.
 * Commission is applied only when known from a legal source.
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
        val distanceKm = GeoMath.distanceKm(input.driverLocation, input.zone.center)
        val travelMin = input.travelTimeMinutes
            ?: GeoMath.estimateTravelMinutes(distanceKm)
        val trafficMin = input.travelTimeWithTrafficMinutes
        val effectiveTravelMin = trafficMin ?: travelMin

        val fuelCost = distanceKm * input.settings.fuelCostPerKm
        val timeCost = effectiveTravelMin * input.settings.timeCostPerMinute
        val travelCost = fuelCost + timeCost + input.tollCost

        val expectedGrossExtra = when {
            input.fareEstimate != null && input.fareWithoutCoefficient != null -> {
                val withCoef = input.fareEstimate.range.midpoint()
                val without = input.fareWithoutCoefficient.range.midpoint()
                max(0.0, withCoef - without)
            }
            else -> input.zone.extraIncome * survivalFactor(input.zone)
        }

        val commissionKnown = input.knownCommissionRate != null || input.knownCommissionFixed != null
        val commissionAmount = when {
            input.knownCommissionFixed != null -> input.knownCommissionFixed
            input.knownCommissionRate != null -> expectedGrossExtra * input.knownCommissionRate
            else -> null
        }

        val afterCommission = expectedGrossExtra - (commissionAmount ?: 0.0)
        val expectedNet = afterCommission - travelCost

        val bearing = GeoMath.bearingDegrees(input.driverLocation, input.zone.center)
        val breakdown = buildList {
            add("Доп. доход (ориент.): ${fmt(expectedGrossExtra)} ${currency(input)}")
            add("Топливо (${fmt(distanceKm)} км × ${fmt(input.settings.fuelCostPerKm)}): −${fmt(fuelCost)}")
            add("Стоимость времени ($effectiveTravelMin мин × ${fmt(input.settings.timeCostPerMinute)}): −${fmt(timeCost)}")
            if (input.tollCost > 0) add("Платные дороги: −${fmt(input.tollCost)}")
            if (commissionKnown) {
                add("Комиссия: −${fmt(commissionAmount ?: 0.0)}")
            } else {
                add("Комиссия не учтена: данные отсутствуют")
            }
            add("Выгода = доп. доход − расходы${if (commissionKnown) " − комиссия" else ""} = ${fmt(expectedNet)}")
            add("Коэффициент может измениться до прибытия. Расчёт приблизительный.")
            if (input.zone.isDemo) {
                add("Демонстрационные данные — не использовать для рабочих решений")
            }
        }

        return ZoneBenefitScore(
            zone = input.zone,
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
            confidence = input.zone.confidence * if (input.zone.isStale()) 0.6 else 1.0,
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
        }
        return sorted.mapIndexed { index, item -> item.copy(rank = index + 1) }
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

    private fun currency(input: Input): String =
        input.fareEstimate?.range?.currencyCode ?: "—"

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.1f", v)
}
