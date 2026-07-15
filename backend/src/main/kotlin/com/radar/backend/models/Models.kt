package com.radar.backend.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CountryDto(
    val code: String,
    val name: String,
    @SerialName("currency_code") val currencyCode: String,
    val locale: String,
    @SerialName("time_zone_id") val timeZoneId: String
)

@Serializable
data class CityDto(
    val id: String,
    val name: String,
    val region: String,
    val country: CountryDto,
    val latitude: Double,
    val longitude: Double,
    @SerialName("time_zone_id") val timeZoneId: String,
    @SerialName("currency_code") val currencyCode: String,
    @SerialName("vehicle_classes") val vehicleClasses: List<String> = emptyList(),
    @SerialName("demand_available") val demandAvailable: Boolean = false,
    @SerialName("data_availability") val dataAvailability: String = "DEMO_ONLY"
)

@Serializable
data class PointDto(val latitude: Double, val longitude: Double)

@Serializable
data class DemandZoneDto(
    val id: String,
    @SerialName("city_id") val cityId: String,
    @SerialName("district_name") val districtName: String,
    val latitude: Double,
    val longitude: Double,
    val polygon: List<PointDto> = emptyList(),
    val coefficient: Double,
    @SerialName("coefficient_type") val coefficientType: String = "SURGE",
    @SerialName("base_income") val baseIncome: Double = 0.0,
    @SerialName("extra_income") val extraIncome: Double = 0.0,
    @SerialName("fetched_at") val fetchedAt: Long,
    @SerialName("valid_until") val validUntil: Long,
    @SerialName("source_name") val sourceName: String,
    @SerialName("source_type") val sourceType: String,
    @SerialName("is_real_data") val isRealData: Boolean,
    @SerialName("is_demo") val isDemo: Boolean,
    val confidence: Double = 0.5,
    @SerialName("demand_level") val demandLevel: String = "ELEVATED",
    @SerialName("vehicle_classes") val vehicleClasses: List<String> = emptyList(),
    @SerialName("survival_probability") val survivalProbability: Double? = null
)

@Serializable
data class RealtimeStatusDto(val available: Boolean)

@Serializable
data class HealthDto(
    val status: String,
    val officialProviderConfigured: Boolean,
    val note: String
)
