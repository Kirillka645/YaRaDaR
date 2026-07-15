package com.radar.coefficients.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_zones")
data class DemandZoneEntity(
    @PrimaryKey val id: String,
    val cityId: String,
    val districtName: String,
    val latitude: Double,
    val longitude: Double,
    val polygonJson: String,
    val coefficient: Double,
    val coefficientType: String,
    val baseIncome: Double,
    val extraIncome: Double,
    val fetchedAtEpochMs: Long,
    val validUntilEpochMs: Long,
    val sourceName: String,
    val sourceType: String,
    val isRealData: Boolean,
    val isDemo: Boolean,
    val confidence: Double,
    val demandLevel: String,
    val vehicleClassesCsv: String,
    val survivalProbability: Double?
)

@Entity(tableName = "cached_cities")
data class CityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String,
    val countryCode: String,
    val countryName: String,
    val currencyCode: String,
    val locale: String,
    val timeZoneId: String,
    val latitude: Double,
    val longitude: Double,
    val vehicleClassesCsv: String,
    val demandDataAvailable: Boolean,
    val dataAvailability: String,
    val cachedAtEpochMs: Long
)

@Entity(tableName = "cached_tariffs")
data class TariffEntity(
    @PrimaryKey val id: String,
    val cityId: String,
    val vehicleClass: String,
    val jsonPayload: String,
    val updatedAtEpochMs: Long
)
