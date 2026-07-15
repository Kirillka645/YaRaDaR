package com.radar.coefficients.domain.model

data class TaxiTariff(
    val id: String,
    val cityId: String,
    val vehicleClass: VehicleClass,
    val minimumFare: Double,
    val includedDistanceKm: Double,
    val includedMinutes: Double,
    val pricePerKm: Double,
    val pricePerMinute: Double,
    val fixedPickupPrice: Double,
    val surcharges: List<TariffSurcharge>,
    val currencyCode: String,
    val updatedAtEpochMs: Long,
    val sourceName: String,
    val isOfficial: Boolean
)

data class TariffSurcharge(
    val name: String,
    val amount: Double,
    val isPercentage: Boolean = false
)

data class FareEstimateRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val vehicleClass: VehicleClass,
    val coefficient: Double,
    val cityId: String,
    val distanceKm: Double,
    val durationMinutes: Double,
    val durationWithTrafficMinutes: Double? = null,
    val includeTolls: Boolean = false,
    val tollAmount: Double = 0.0
)

data class FareEstimate(
    val range: MoneyRange,
    val baseWithoutCoefficient: MoneyRange,
    val coefficientApplied: Double,
    val breakdown: List<String>,
    val isApproximate: Boolean,
    val disclaimerRu: String = DISCLAIMER,
    val sourceName: String,
    val calculatedAtEpochMs: Long
) {
    companion object {
        const val DISCLAIMER =
            "Это ориентировочный расчёт, а не гарантированная стоимость заказа. " +
                "Итоговая цена определяется сервисом такси при оформлении поездки."
    }
}

data class RouteEstimate(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val distanceKm: Double,
    val durationMinutes: Int,
    val durationWithTrafficMinutes: Int?,
    val geometry: List<GeoPoint>,
    val hasTrafficData: Boolean,
    val tollInfo: TollRoadInfo?,
    val sourceName: String,
    val calculatedAtEpochMs: Long
) {
    val trafficNoteRu: String
        get() = if (hasTrafficData) {
            "Время с учётом пробок"
        } else {
            "Время рассчитано без учёта пробок"
        }
}

data class TollRoadInfo(
    val hasTolls: Boolean,
    val estimatedCost: Double?,
    val currencyCode: String
)
