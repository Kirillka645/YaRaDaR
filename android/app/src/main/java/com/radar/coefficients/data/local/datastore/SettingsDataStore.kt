package com.radar.coefficients.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radar.coefficients.domain.model.DisplayCurrency
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.VehicleClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "radar_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val minCoef = doublePreferencesKey("min_coef")
        val notifRadius = intPreferencesKey("notif_radius")
        val refreshMin = intPreferencesKey("refresh_min")
        val mapRadius = intPreferencesKey("map_radius")
        val showTraffic = booleanPreferencesKey("show_traffic")
        val demoMode = booleanPreferencesKey("demo_mode")
        val vehicleClass = stringPreferencesKey("vehicle_class")
        val fuelPerKm = doublePreferencesKey("fuel_per_km")
        val timePerMin = doublePreferencesKey("time_per_min")
        val notifEnabled = booleanPreferencesKey("notif_enabled")
        val selectedCityId = stringPreferencesKey("selected_city_id")
        val lastLat = doublePreferencesKey("last_lat")
        val lastLon = doublePreferencesKey("last_lon")
        val favorites = stringSetPreferencesKey("favorites")
        val recents = stringPreferencesKey("recents")
        val mapTariffs = stringSetPreferencesKey("map_visible_tariffs")
        val displayCurrency = stringPreferencesKey("display_currency")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val onlyHot = booleanPreferencesKey("only_hot_zones")
        val autoRefresh = booleanPreferencesKey("auto_refresh")
        val showMoney = booleanPreferencesKey("show_money_map")
        val compactBubble = booleanPreferencesKey("compact_bubble")
        val shiftStart = longPreferencesKey("shift_start")
        val shiftZones = intPreferencesKey("shift_zones")
        val showForecast = booleanPreferencesKey("show_forecast")
        val highlightIgnite = booleanPreferencesKey("highlight_ignite")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun getSettings(): UserSettings {
        var result = UserSettings()
        context.dataStore.edit { prefs -> result = prefs.toSettings() }
        return result
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[Keys.minCoef] = next.minCoefficientAlert
            prefs[Keys.notifRadius] = next.notificationRadiusKm
            prefs[Keys.refreshMin] = next.refreshIntervalMinutes
            prefs[Keys.mapRadius] = next.mapRadiusKm
            prefs[Keys.showTraffic] = next.showTraffic
            prefs[Keys.demoMode] = next.demoModeEnabled
            prefs[Keys.vehicleClass] = next.selectedVehicleClass.name
            prefs[Keys.fuelPerKm] = next.fuelCostPerKm
            prefs[Keys.timePerMin] = next.timeCostPerMinute
            prefs[Keys.notifEnabled] = next.notificationsEnabled
            if (next.selectedCityId != null) {
                prefs[Keys.selectedCityId] = next.selectedCityId
            } else {
                prefs.remove(Keys.selectedCityId)
            }
            next.lastKnownLatitude?.let { prefs[Keys.lastLat] = it }
            next.lastKnownLongitude?.let { prefs[Keys.lastLon] = it }
            prefs[Keys.favorites] = next.favoriteCityIds
            prefs[Keys.recents] = next.recentCityIds.joinToString(",")
            prefs[Keys.mapTariffs] = next.mapVisibleTariffs.map { it.name }.toSet()
            prefs[Keys.displayCurrency] = next.displayCurrency.code
            prefs[Keys.keepScreenOn] = next.keepScreenOn
            prefs[Keys.onlyHot] = next.showOnlyHotZones
            prefs[Keys.autoRefresh] = next.autoRefreshEnabled
            prefs[Keys.showMoney] = next.showMoneyOnMap
            prefs[Keys.compactBubble] = next.compactDriverBubble
            prefs[Keys.shiftStart] = next.shiftStartedAtEpochMs
            prefs[Keys.shiftZones] = next.shiftZonesChecked
            prefs[Keys.showForecast] = next.showForecastAndOrders
            prefs[Keys.highlightIgnite] = next.highlightPredictedIgnite
        }
    }

    private fun Preferences.toSettings(): UserSettings {
        val recents = this[Keys.recents]?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val mapTariffs = this[Keys.mapTariffs]
            ?.mapNotNull { runCatching { VehicleClass.valueOf(it) }.getOrNull() }
            ?.toSet()
            ?.ifEmpty { null }
            ?: UserSettings.DEFAULT_MAP_TARIFFS
        return UserSettings(
            minCoefficientAlert = this[Keys.minCoef] ?: 1.5,
            notificationRadiusKm = this[Keys.notifRadius] ?: 5,
            refreshIntervalMinutes = this[Keys.refreshMin] ?: 3,
            mapRadiusKm = this[Keys.mapRadius] ?: 10,
            showTraffic = this[Keys.showTraffic] ?: true,
            demoModeEnabled = this[Keys.demoMode] ?: false,
            selectedVehicleClass = this[Keys.vehicleClass]?.let {
                runCatching { VehicleClass.valueOf(it) }.getOrNull()
            } ?: VehicleClass.ECONOMY,
            fuelCostPerKm = this[Keys.fuelPerKm] ?: 6.0,
            timeCostPerMinute = this[Keys.timePerMin] ?: 5.0,
            notificationsEnabled = this[Keys.notifEnabled] ?: true,
            selectedCityId = this[Keys.selectedCityId],
            lastKnownLatitude = this[Keys.lastLat],
            lastKnownLongitude = this[Keys.lastLon],
            favoriteCityIds = this[Keys.favorites] ?: emptySet(),
            recentCityIds = recents,
            mapVisibleTariffs = mapTariffs,
            displayCurrency = DisplayCurrency.fromCode(this[Keys.displayCurrency] ?: "RUB"),
            keepScreenOn = this[Keys.keepScreenOn] ?: true,
            showOnlyHotZones = this[Keys.onlyHot] ?: false,
            autoRefreshEnabled = this[Keys.autoRefresh] ?: true,
            showMoneyOnMap = this[Keys.showMoney] ?: true,
            compactDriverBubble = this[Keys.compactBubble] ?: false,
            shiftStartedAtEpochMs = this[Keys.shiftStart] ?: 0L,
            shiftZonesChecked = this[Keys.shiftZones] ?: 0,
            showForecastAndOrders = this[Keys.showForecast] ?: true,
            highlightPredictedIgnite = this[Keys.highlightIgnite] ?: true
        )
    }
}
