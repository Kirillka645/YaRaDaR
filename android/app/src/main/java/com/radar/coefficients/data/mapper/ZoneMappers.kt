package com.radar.coefficients.data.mapper

import com.radar.coefficients.data.local.entity.CityEntity
import com.radar.coefficients.data.local.entity.DemandZoneEntity
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.VehicleClass
import org.json.JSONArray

fun DemandZone.toEntity(): DemandZoneEntity = DemandZoneEntity(
    id = id,
    cityId = cityId,
    districtName = districtName,
    latitude = center.latitude,
    longitude = center.longitude,
    polygonJson = polygonToJson(polygon),
    coefficient = coefficient,
    coefficientType = coefficientType.name,
    baseIncome = baseIncome,
    extraIncome = extraIncome,
    fetchedAtEpochMs = fetchedAtEpochMs,
    validUntilEpochMs = validUntilEpochMs,
    sourceName = sourceName,
    sourceType = sourceType.name,
    isRealData = isRealData,
    isDemo = isDemo,
    confidence = confidence,
    demandLevel = demandLevel.name,
    vehicleClassesCsv = availableVehicleClasses.joinToString(",") { it.name },
    survivalProbability = survivalProbability
)

fun DemandZoneEntity.toDomain(): DemandZone = DemandZone(
    id = id,
    cityId = cityId,
    districtName = districtName,
    center = GeoPoint(latitude, longitude),
    polygon = polygonFromJson(polygonJson),
    coefficient = coefficient,
    coefficientType = runCatching { CoefficientType.valueOf(coefficientType) }
        .getOrDefault(CoefficientType.UNKNOWN),
    baseIncome = baseIncome,
    extraIncome = extraIncome,
    fetchedAtEpochMs = fetchedAtEpochMs,
    validUntilEpochMs = validUntilEpochMs,
    sourceName = sourceName,
    sourceType = runCatching { SourceType.valueOf(sourceType) }
        .getOrDefault(SourceType.DEMO_PROVIDER),
    isRealData = isRealData,
    isDemo = isDemo,
    confidence = confidence,
    demandLevel = runCatching { DemandLevel.valueOf(demandLevel) }
        .getOrDefault(DemandLevel.ELEVATED),
    availableVehicleClasses = vehicleClassesCsv.split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { VehicleClass.valueOf(it) }.getOrNull() },
    survivalProbability = survivalProbability
)

fun City.toEntity(now: Long = System.currentTimeMillis()): CityEntity = CityEntity(
    id = id,
    name = name,
    region = region,
    countryCode = country.code,
    countryName = country.name,
    currencyCode = currencyCode,
    locale = country.locale,
    timeZoneId = timeZoneId,
    latitude = center.latitude,
    longitude = center.longitude,
    vehicleClassesCsv = availableVehicleClasses.joinToString(",") { it.name },
    demandDataAvailable = demandDataAvailable,
    dataAvailability = dataAvailability.name,
    cachedAtEpochMs = now
)

fun CityEntity.toDomain(): City = City(
    id = id,
    name = name,
    region = region,
    country = Country(
        code = countryCode,
        name = countryName,
        currencyCode = currencyCode,
        locale = locale,
        timeZoneId = timeZoneId
    ),
    center = GeoPoint(latitude, longitude),
    bounds = null,
    timeZoneId = timeZoneId,
    currencyCode = currencyCode,
    availableVehicleClasses = vehicleClassesCsv.split(",")
        .filter { it.isNotBlank() }
        .mapNotNull { runCatching { VehicleClass.valueOf(it) }.getOrNull() },
    availableTariffs = emptyList(),
    demandDataAvailable = demandDataAvailable,
    dataAvailability = runCatching { CityDataAvailability.valueOf(dataAvailability) }
        .getOrDefault(CityDataAvailability.DEMO_ONLY)
)

private fun polygonToJson(points: List<GeoPoint>): String {
    val arr = JSONArray()
    points.forEach { p ->
        val o = JSONArray()
        o.put(p.latitude)
        o.put(p.longitude)
        arr.put(o)
    }
    return arr.toString()
}

private fun polygonFromJson(json: String): List<GeoPoint> = runCatching {
    val arr = JSONArray(json)
    buildList {
        for (i in 0 until arr.length()) {
            val p = arr.getJSONArray(i)
            add(GeoPoint(p.getDouble(0), p.getDouble(1)))
        }
    }
}.getOrDefault(emptyList())
