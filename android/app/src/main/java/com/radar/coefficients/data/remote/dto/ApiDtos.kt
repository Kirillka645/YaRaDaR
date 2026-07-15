package com.radar.coefficients.data.remote.dto

import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CountryDto(
    val code: String,
    val name: String,
    @Json(name = "currency_code") val currencyCode: String,
    val locale: String,
    @Json(name = "time_zone_id") val timeZoneId: String
)

@JsonClass(generateAdapter = true)
data class CityDto(
    val id: String,
    val name: String,
    val region: String,
    val country: CountryDto,
    val latitude: Double,
    val longitude: Double,
    @Json(name = "time_zone_id") val timeZoneId: String,
    @Json(name = "currency_code") val currencyCode: String,
    @Json(name = "vehicle_classes") val vehicleClasses: List<String> = emptyList(),
    @Json(name = "demand_available") val demandAvailable: Boolean = false,
    @Json(name = "data_availability") val dataAvailability: String = "DEMO_ONLY"
)

@JsonClass(generateAdapter = true)
data class DemandZoneDto(
    val id: String,
    @Json(name = "city_id") val cityId: String,
    @Json(name = "district_name") val districtName: String,
    val latitude: Double,
    val longitude: Double,
    val polygon: List<PointDto> = emptyList(),
    val coefficient: Double,
    @Json(name = "coefficient_type") val coefficientType: String = "SURGE",
    @Json(name = "base_income") val baseIncome: Double = 0.0,
    @Json(name = "extra_income") val extraIncome: Double = 0.0,
    @Json(name = "fetched_at") val fetchedAt: Long,
    @Json(name = "valid_until") val validUntil: Long,
    @Json(name = "source_name") val sourceName: String,
    @Json(name = "source_type") val sourceType: String,
    @Json(name = "is_real_data") val isRealData: Boolean,
    @Json(name = "is_demo") val isDemo: Boolean,
    val confidence: Double = 0.5,
    @Json(name = "demand_level") val demandLevel: String = "ELEVATED",
    @Json(name = "vehicle_classes") val vehicleClasses: List<String> = emptyList(),
    @Json(name = "survival_probability") val survivalProbability: Double? = null
)

@JsonClass(generateAdapter = true)
data class PointDto(val latitude: Double, val longitude: Double)

@JsonClass(generateAdapter = true)
data class RealtimeStatusDto(val available: Boolean)

fun CountryDto.toDomain() = Country(code, name, currencyCode, locale, timeZoneId)

fun CityDto.toDomain() = City(
    id = id,
    name = name,
    region = region,
    country = country.toDomain(),
    center = GeoPoint(latitude, longitude),
    bounds = null,
    timeZoneId = timeZoneId,
    currencyCode = currencyCode,
    availableVehicleClasses = vehicleClasses.mapNotNull { runCatching { VehicleClass.valueOf(it) }.getOrNull() },
    availableTariffs = emptyList(),
    demandDataAvailable = demandAvailable,
    dataAvailability = runCatching { CityDataAvailability.valueOf(dataAvailability) }
        .getOrDefault(CityDataAvailability.DEMO_ONLY)
)

fun DemandZoneDto.toDomain() = DemandZone(
    id = id,
    cityId = cityId,
    districtName = districtName,
    center = GeoPoint(latitude, longitude),
    polygon = polygon.map { GeoPoint(it.latitude, it.longitude) }
        .ifEmpty { listOf(GeoPoint(latitude, longitude)) },
    coefficient = coefficient,
    coefficientType = runCatching { CoefficientType.valueOf(coefficientType) }
        .getOrDefault(CoefficientType.UNKNOWN),
    baseIncome = baseIncome,
    extraIncome = extraIncome,
    fetchedAtEpochMs = fetchedAt,
    validUntilEpochMs = validUntil,
    sourceName = sourceName,
    sourceType = runCatching { SourceType.valueOf(sourceType) }
        .getOrDefault(SourceType.PARTNER_API),
    isRealData = isRealData && !isDemo,
    isDemo = isDemo,
    confidence = confidence,
    demandLevel = runCatching { DemandLevel.valueOf(demandLevel) }
        .getOrDefault(DemandLevel.ELEVATED),
    availableVehicleClasses = vehicleClasses.mapNotNull {
        runCatching { VehicleClass.valueOf(it) }.getOrNull()
    },
    survivalProbability = survivalProbability
)
