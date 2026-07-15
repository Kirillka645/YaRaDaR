package com.radar.coefficients.data.remote.api

import com.radar.coefficients.data.remote.dto.CityDto
import com.radar.coefficients.data.remote.dto.CountryDto
import com.radar.coefficients.data.remote.dto.DemandZoneDto
import com.radar.coefficients.data.remote.dto.RealtimeStatusDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DemandApi {
    @GET("v1/countries")
    suspend fun getCountries(): List<CountryDto>

    @GET("v1/countries/{code}/cities")
    suspend fun getCities(@Path("code") countryCode: String): List<CityDto>

    @GET("v1/cities/detect")
    suspend fun detectCity(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): CityDto?

    @GET("v1/cities/{cityId}/zones")
    suspend fun getZones(
        @Path("cityId") cityId: String,
        @Query("sw_lat") swLat: Double? = null,
        @Query("sw_lon") swLon: Double? = null,
        @Query("ne_lat") neLat: Double? = null,
        @Query("ne_lon") neLon: Double? = null
    ): List<DemandZoneDto>

    @GET("v1/zones/{zoneId}")
    suspend fun getZone(@Path("zoneId") zoneId: String): DemandZoneDto?

    @GET("v1/cities/{cityId}/realtime")
    suspend fun isRealtime(@Path("cityId") cityId: String): RealtimeStatusDto
}
