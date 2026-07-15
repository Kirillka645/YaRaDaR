package com.radar.coefficients.domain.model

/**
 * Demand / surge coefficient models with strict data-source honesty.
 * Never label mock, cache, or random values as real.
 */

enum class SourceType {
    OFFICIAL_API,
    LICENSED_PROVIDER,
    PARTNER_API,
    MANUAL_DRIVER_REPORT,
    DEMO_PROVIDER
}

enum class CoefficientType {
    SURGE,
    DEMAND,
    FIXED_BONUS,
    UNKNOWN
}

enum class DemandLevel {
    NORMAL,
    ELEVATED,
    HIGH,
    CRITICAL
}

enum class DataStatus {
    REAL,
    PARTNER,
    COMMUNITY,
    STALE,
    DEMO,
    NONE
}

enum class ForecastTrend {
    RISING,
    FALLING,
    STABLE
}

/**
 * Прогноз спроса (модель YaRaDaR, не данные Яндекса).
 * Показывает, куда «может загореть» кэф через N минут.
 */
data class ZoneForecast(
    val coefficientIn15Min: Double,
    val coefficientIn30Min: Double,
    val coefficientIn60Min: Double,
    val trend: ForecastTrend,
    /** Вероятность, что кэф ≥ порога через 30 мин (0..1) */
    val igniteProbability30Min: Double,
    /** Через сколько минут ожидается рост выше текущего (null = не ожидается) */
    val minutesToIgnite: Int?,
    val confidence: Double,
    val summaryRu: String
)

/** Оценка заказов по району (модель, ориентир). */
data class DistrictOrderStats(
    val ordersLast15Min: Int,
    val ordersLastHour: Int,
    val ordersToday: Int,
    val avgOrdersPerHour: Double,
    val peakHourLocal: Int,
    val demandPressure: Double
)

data class DemandZone(
    val id: String,
    val cityId: String,
    val districtName: String,
    val center: GeoPoint,
    val polygon: List<GeoPoint>,
    /** Основной коэффициент (обычно Эконом) — для рейтинга и цвета зоны */
    val coefficient: Double,
    val coefficientType: CoefficientType,
    val baseIncome: Double,
    val extraIncome: Double,
    val fetchedAtEpochMs: Long,
    val validUntilEpochMs: Long,
    val sourceName: String,
    val sourceType: SourceType,
    val isRealData: Boolean,
    val isDemo: Boolean,
    val confidence: Double,
    val demandLevel: DemandLevel,
    val availableVehicleClasses: List<VehicleClass>,
    val survivalProbability: Double? = null,
    /** Коэффициенты по тарифам: Эконом, Комфорт, Детский… */
    val coefficientsByClass: Map<VehicleClass, Double> = emptyMap(),
    val forecast: ZoneForecast? = null,
    val orderStats: DistrictOrderStats? = null,
    /** 0..100 — «жар» района (кэф + заказы + прогноз) */
    val heatScore: Int = 0,
    val districtKind: String = "generic"
) {
    fun coefficientFor(vehicleClass: VehicleClass): Double =
        coefficientsByClass[vehicleClass] ?: coefficient

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs > validUntilEpochMs

    fun isStale(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 10 * 60_000L): Boolean =
        isExpired(nowMs) || (nowMs - fetchedAtEpochMs) > maxAgeMs

    fun willLikelyIgnite(minCoef: Double = 1.5): Boolean {
        val f = forecast ?: return false
        return f.coefficientIn30Min >= minCoef && f.igniteProbability30Min >= 0.55
    }

    fun dataStatus(nowMs: Long = System.currentTimeMillis()): DataStatus = when {
        isDemo || sourceType == SourceType.DEMO_PROVIDER -> DataStatus.DEMO
        isStale(nowMs) -> DataStatus.STALE
        sourceType == SourceType.MANUAL_DRIVER_REPORT -> DataStatus.COMMUNITY
        sourceType == SourceType.PARTNER_API || sourceType == SourceType.LICENSED_PROVIDER ->
            if (isRealData) DataStatus.PARTNER else DataStatus.NONE
        sourceType == SourceType.OFFICIAL_API && isRealData -> DataStatus.REAL
        else -> DataStatus.NONE
    }
}

/** Строка для подписи над машинкой: «Э ×1.5» */
data class TariffCoefLabel(
    val vehicleClass: VehicleClass,
    val coefficient: Double
) {
    val mapText: String
        get() = "${vehicleClass.shortLabel} ×${"%.1f".format(coefficient)}"
}

data class ZoneBenefitScore(
    val zone: DemandZone,
    val rank: Int,
    val distanceKm: Double,
    val travelTimeMinutes: Int,
    val travelTimeWithTrafficMinutes: Int?,
    val fuelCost: Double,
    val timeCost: Double,
    val expectedGrossExtra: Double,
    val expectedNetBenefit: Double,
    val estimatedFareRange: MoneyRange?,
    val fareWithoutCoefficientRange: MoneyRange?,
    val directionBearingDegrees: Float,
    val directionLabelRu: String,
    val confidence: Double,
    val commissionKnown: Boolean,
    val commissionAmount: Double?,
    val calculationBreakdown: List<String>
)

data class MoneyRange(
    val min: Double,
    val max: Double,
    val currencyCode: String
) {
    fun midpoint(): Double = (min + max) / 2.0
}

enum class ProviderConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    NOT_CONFIGURED,
    RATE_LIMITED,
    ERROR,
    DEMO
}

data class ProviderStatus(
    val name: String,
    val sourceType: SourceType,
    val status: ProviderConnectionStatus,
    val lastUpdatedAtEpochMs: Long?,
    val supportedCitiesHint: String,
    val termsOfUse: String,
    val isDemo: Boolean
)
