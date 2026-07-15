package com.radar.coefficients.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.radar.coefficients.data.local.entity.CityEntity
import com.radar.coefficients.data.local.entity.DemandZoneEntity
import com.radar.coefficients.data.local.entity.TariffEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DemandZoneDao {
    @Query("SELECT * FROM cached_zones WHERE cityId = :cityId")
    fun observeByCity(cityId: String): Flow<List<DemandZoneEntity>>

    @Query("SELECT * FROM cached_zones WHERE cityId = :cityId")
    suspend fun getByCity(cityId: String): List<DemandZoneEntity>

    @Query("SELECT * FROM cached_zones WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DemandZoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DemandZoneEntity>)

    @Query("DELETE FROM cached_zones WHERE cityId = :cityId")
    suspend fun clearCity(cityId: String)

    @Query("DELETE FROM cached_zones WHERE validUntilEpochMs < :now")
    suspend fun clearExpired(now: Long)

    @Query("SELECT MAX(fetchedAtEpochMs) FROM cached_zones")
    suspend fun lastFetched(): Long?
}

@Dao
interface CityDao {
    @Query("SELECT * FROM cached_cities WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CityEntity?

    @Query("SELECT * FROM cached_cities WHERE name LIKE '%' || :query || '%' OR region LIKE '%' || :query || '%' LIMIT 50")
    suspend fun search(query: String): List<CityEntity>

    @Query("SELECT * FROM cached_cities WHERE countryCode = :code")
    suspend fun byCountry(code: String): List<CityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CityEntity>)
}

@Dao
interface TariffDao {
    @Query("SELECT * FROM cached_tariffs WHERE cityId = :cityId")
    suspend fun byCity(cityId: String): List<TariffEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TariffEntity>)
}
