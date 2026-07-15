package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.FareEstimate
import com.radar.coefficients.domain.model.FareEstimateRequest
import com.radar.coefficients.domain.model.MoneyRange
import com.radar.coefficients.domain.model.TaxiTariff
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Approximate fare from published / configured tariffs.
 * Uses range presentation when calculation is approximate.
 */
object FareCalculator {

    fun estimate(
        tariff: TaxiTariff,
        request: FareEstimateRequest,
        sourceName: String = tariff.sourceName
    ): FareEstimate {
        val duration = request.durationWithTrafficMinutes ?: request.durationMinutes
        val distanceCharge = max(0.0, request.distanceKm - tariff.includedDistanceKm) * tariff.pricePerKm
        val timeCharge = max(0.0, duration - tariff.includedMinutes) * tariff.pricePerMinute
        val rawBase = tariff.fixedPickupPrice + distanceCharge + timeCharge
        val base = max(tariff.minimumFare, rawBase)

        var surchargesFlat = 0.0
        var surchargesPct = 0.0
        tariff.surcharges.forEach { s ->
            if (s.isPercentage) surchargesPct += s.amount else surchargesFlat += s.amount
        }
        if (request.includeTolls) surchargesFlat += request.tollAmount

        val baseWithSurcharges = base * (1 + surchargesPct) + surchargesFlat
        val withCoef = baseWithSurcharges * request.coefficient

        val baseRange = rangeAround(baseWithSurcharges, tariff.currencyCode)
        val coefRange = rangeAround(withCoef, tariff.currencyCode)

        val breakdown = listOf(
            "Минимум: ${tariff.minimumFare} ${tariff.currencyCode}",
            "Подача: ${tariff.fixedPickupPrice}",
            "Расстояние: ${fmt(request.distanceKm)} км → ${fmt(distanceCharge)}",
            "Время: ${fmt(duration)} мин → ${fmt(timeCharge)}",
            "База: ${fmt(baseWithSurcharges)}",
            "Коэффициент ×${fmt(request.coefficient)} → ${fmt(withCoef)}",
            FareEstimate.DISCLAIMER
        )

        return FareEstimate(
            range = coefRange,
            baseWithoutCoefficient = baseRange,
            coefficientApplied = request.coefficient,
            breakdown = breakdown,
            isApproximate = true,
            sourceName = sourceName,
            calculatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun rangeAround(value: Double, currency: String, spread: Double = 0.12): MoneyRange {
        val min = (value * (1 - spread)).roundToInt().toDouble().coerceAtLeast(0.0)
        val max = (value * (1 + spread)).roundToInt().toDouble()
        return MoneyRange(min, max, currency)
    }

    private fun fmt(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString() else String.format("%.2f", v)
}
