package com.radar.coefficients.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.radar.coefficients.data.local.dao.CityDao
import com.radar.coefficients.data.local.dao.DemandZoneDao
import com.radar.coefficients.data.local.dao.TariffDao
import com.radar.coefficients.data.local.entity.CityEntity
import com.radar.coefficients.data.local.entity.DemandZoneEntity
import com.radar.coefficients.data.local.entity.TariffEntity

@Database(
    entities = [DemandZoneEntity::class, CityEntity::class, TariffEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun demandZoneDao(): DemandZoneDao
    abstract fun cityDao(): CityDao
    abstract fun tariffDao(): TariffDao
}
