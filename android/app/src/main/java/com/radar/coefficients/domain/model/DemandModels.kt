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

data class DemandZone(
    val id: String,
    val cityId: String,
    val districtName: String,
    val center: GeoPoint,
    val polygon: List<GeoPoint>,
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
    val survivalProbability: Double? = null
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs > validUntilEpochMs

    fun isStale(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 10 * 60_000L): Boolean =
        isExpired(nowMs) || (nowMs - fetchedAtEpochMs) > maxAgeMs

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
