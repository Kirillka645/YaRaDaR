package com.radar.coefficients.data.repository

import com.radar.coefficients.data.local.datastore.SettingsDataStore
import com.radar.coefficients.data.provider.CompositeDemandCoefficientProvider
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore,
    private val composite: CompositeDemandCoefficientProvider
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> = dataStore.settingsFlow

    override suspend fun getSettings(): UserSettings = dataStore.getSettings().also {
        composite.allowDemoFallback = it.demoModeEnabled
    }

    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        dataStore.update(transform)
        composite.allowDemoFallback = dataStore.getSettings().demoModeEnabled
    }
}
