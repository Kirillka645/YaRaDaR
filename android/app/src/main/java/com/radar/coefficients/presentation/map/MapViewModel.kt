package com.radar.coefficients.presentation.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.radar.coefficients.data.provider.TariffFareEstimateProvider
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.FareEstimate
import com.radar.coefficients.domain.model.FareEstimateRequest
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.MapRadiusFilter
import com.radar.coefficients.domain.model.TariffCoefLabel
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.model.ZoneBenefitScore
import com.radar.coefficients.data.provider.YaRadarOfficialEngine
import com.radar.coefficients.domain.repository.CityRepository
import com.radar.coefficients.domain.repository.DemandRepository
import com.radar.coefficients.domain.repository.RouteRepository
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.usecase.BenefitCalculator
import com.radar.coefficients.domain.util.GeoMath
import com.radar.coefficients.presentation.common.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class MapUiState(
    val isLoading: Boolean = true,
    val zones: List<DemandZone> = emptyList(),
    val filteredZones: List<DemandZone> = emptyList(),
    val selectedZone: DemandZone? = null,
    val selectedScore: ZoneBenefitScore? = null,
    val selectedFare: FareEstimate? = null,
    val selectedFareBase: FareEstimate? = null,
    val driverLocation: GeoPoint? = null,
    /** Кэфы выбранных тарифов в текущей точке (над машинкой) */
    val driverTariffLabels: List<TariffCoefLabel> = emptyList(),
    val localZone: DemandZone? = null,
    val city: City? = null,
    val settings: UserSettings = UserSettings(),
    val lastUpdatedAt: Long? = null,
    val showTraffic: Boolean = true,
    val radius: MapRadiusFilter = MapRadiusFilter.R10,
    val message: UiMessage? = null,
    val realDataAvailable: Boolean = false,
    val pendingCitySwitch: City? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val demandRepository: DemandRepository,
    private val cityRepository: CityRepository,
    private val settingsRepository: SettingsRepository,
    private val routeRepository: RouteRepository,
    private val fareProvider: TariffFareEstimateProvider
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observeSettings(),
                cityRepository.observeSelectedCity()
            ) { settings, city -> settings to city }
                .collect { (settings, city) ->
                    _state.update {
                        val labels = buildDriverLabels(
                            location = it.driverLocation,
                            zones = it.filteredZones.ifEmpty { it.zones },
                            visible = settings.mapVisibleTariffs
                        )
                        it.copy(
                            settings = settings,
                            city = city,
                            driverTariffLabels = labels.first,
                            localZone = labels.second,
                            showTraffic = settings.showTraffic,
                            radius = MapRadiusFilter.fromKm(settings.mapRadiusKm)
                        )
                    }
                    if (city != null) {
                        refresh()
                    }
                }
        }
    }

    fun bootstrapLocation() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            val loc = fetchLocation()
            if (loc == null) {
                if (settings.selectedCityId == null) {
                    // No geo: still allow demo for any seed city (not hard-locked to Moscow)
                    val fallback = cityRepository.searchCities("").firstOrNull()
                    if (fallback != null) {
                        cityRepository.setSelectedCity(fallback)
                        _state.update {
                            it.copy(
                                driverLocation = fallback.center,
                                message = UiMessage.NoLocation
                            )
                        }
                    } else {
                        _state.update { it.copy(message = UiMessage.NoLocation, isLoading = false) }
                    }
                } else {
                    settings.selectedCityId?.let { id ->
                        cityRepository.getCity(id)?.let { city ->
                            _state.update {
                                it.copy(driverLocation = it.driverLocation ?: city.center)
                            }
                            refresh()
                        }
                    }
                }
                return@launch
            }
            val point = GeoPoint(loc.latitude, loc.longitude)
            _state.update {
                val labels = buildDriverLabels(
                    point,
                    it.filteredZones.ifEmpty { it.zones },
                    it.settings.mapVisibleTariffs
                )
                it.copy(
                    driverLocation = point,
                    driverTariffLabels = labels.first,
                    localZone = labels.second
                )
            }
            settingsRepository.updateSettings {
                it.copy(lastKnownLatitude = point.latitude, lastKnownLongitude = point.longitude)
            }
            val detected = cityRepository.detectCity(point.latitude, point.longitude)
            val currentId = settingsRepository.getSettings().selectedCityId
            if (detected != null) {
                if (currentId == null) {
                    cityRepository.setSelectedCity(detected)
                } else if (currentId != detected.id) {
                    _state.update { it.copy(pendingCitySwitch = detected) }
                } else {
                    refresh()
                }
            } else if (currentId == null) {
                val fallback = cityRepository.searchCities("").firstOrNull()
                fallback?.let { cityRepository.setSelectedCity(it) }
            }
        }
    }

    fun confirmCitySwitch(accept: Boolean) {
        viewModelScope.launch {
            val pending = _state.value.pendingCitySwitch
            _state.update { it.copy(pendingCitySwitch = null) }
            if (accept && pending != null) {
                cityRepository.setSelectedCity(pending)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val city = _state.value.city
                ?: cityRepository.getCity(settingsRepository.getSettings().selectedCityId.orEmpty())
            if (city == null) {
                _state.update { it.copy(isLoading = false, message = UiMessage.NoLocation) }
                return@launch
            }
            _state.update { it.copy(isLoading = true, message = UiMessage.Loading, city = city) }
            fareProvider.ensureDefaultTariffs(city.id, city.currencyCode)
            val result = demandRepository.refreshZones(city.id, null)
            val real = demandRepository.isRealTimeAvailable(city.id)
            result.fold(
                onSuccess = { zones ->
                    val filtered = filterZones(zones, _state.value.driverLocation, _state.value.radius)
                    val allStale = zones.isNotEmpty() && zones.all { it.isStale() }
                    _state.update {
                        val labels = buildDriverLabels(
                            it.driverLocation,
                            filtered,
                            it.settings.mapVisibleTariffs
                        )
                        it.copy(
                            isLoading = false,
                            zones = zones,
                            filteredZones = filtered,
                            driverTariffLabels = labels.first,
                            localZone = labels.second,
                            lastUpdatedAt = demandRepository.getLastUpdatedAt(),
                            realDataAvailable = real,
                            message = when {
                                zones.isEmpty() -> UiMessage.EmptyZones
                                allStale -> UiMessage.StaleData
                                !real && zones.all { z -> z.isDemo } -> UiMessage.RealDataUnavailable
                                else -> null
                            }
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = if (e.message?.contains("Unable to resolve", true) == true ||
                                e.message?.contains("Failed to connect", true) == true
                            ) UiMessage.NoInternet else UiMessage.Error(e.message ?: "Неизвестная ошибка")
                        )
                    }
                }
            )
        }
    }

    fun setRadius(filter: MapRadiusFilter) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(mapRadiusKm = filter.km) }
            _state.update {
                val filtered = filterZones(it.zones, it.driverLocation, filter)
                val labels = buildDriverLabels(
                    it.driverLocation,
                    filtered,
                    it.settings.mapVisibleTariffs
                )
                it.copy(
                    radius = filter,
                    filteredZones = filtered,
                    driverTariffLabels = labels.first,
                    localZone = labels.second
                )
            }
        }
    }

    fun toggleTraffic(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(showTraffic = show) }
            _state.update { it.copy(showTraffic = show) }
        }
    }

    fun selectZone(zone: DemandZone?) {
        if (zone == null) {
            _state.update {
                it.copy(selectedZone = null, selectedScore = null, selectedFare = null, selectedFareBase = null)
            }
            return
        }
        viewModelScope.launch {
            val driver = _state.value.driverLocation ?: zone.center
            val settings = _state.value.settings
            val city = _state.value.city
            val route = routeRepository.estimateToZone(driver, zone.center).getOrNull()
            val score = BenefitCalculator.score(
                BenefitCalculator.Input(
                    zone = zone,
                    driverLocation = driver,
                    settings = settings,
                    travelTimeMinutes = route?.durationMinutes,
                    travelTimeWithTrafficMinutes = route?.durationWithTrafficMinutes,
                    tollCost = route?.tollInfo?.estimatedCost ?: 0.0
                )
            )
            var fare: FareEstimate? = null
            var fareBase: FareEstimate? = null
            if (city != null) {
                val shortTripKm = 5.0
                val shortMin = 12.0
                val reqCoef = FareEstimateRequest(
                    origin = zone.center,
                    destination = GeoMath.offsetPoint(zone.center, shortTripKm, 90.0),
                    vehicleClass = settings.selectedVehicleClass,
                    coefficient = zone.coefficient,
                    cityId = city.id,
                    distanceKm = shortTripKm,
                    durationMinutes = shortMin
                )
                val reqBase = reqCoef.copy(coefficient = 1.0)
                runCatching {
                    fare = fareProvider.estimateFare(reqCoef)
                    fareBase = fareProvider.estimateFare(reqBase)
                }
            }
            _state.update {
                it.copy(
                    selectedZone = zone,
                    selectedScore = score,
                    selectedFare = fare,
                    selectedFareBase = fareBase
                )
            }
        }
    }

    fun centerOnDriver() {
        bootstrapLocation()
    }

    private fun filterZones(
        zones: List<DemandZone>,
        driver: GeoPoint?,
        radius: MapRadiusFilter
    ): List<DemandZone> {
        if (driver == null) return zones
        return zones.filter { GeoMath.distanceKm(driver, it.center) <= radius.km }
    }

    /**
     * Берёт ближайшую зону (или зону, в центре которой вы стоите) и
     * собирает кэфы для включённых в настройках тарифов.
     */
    private fun buildDriverLabels(
        location: GeoPoint?,
        zones: List<DemandZone>,
        visible: Set<VehicleClass>
    ): Pair<List<TariffCoefLabel>, DemandZone?> {
        if (location == null || zones.isEmpty() || visible.isEmpty()) {
            return emptyList<TariffCoefLabel>() to null
        }
        val nearest = zones.minByOrNull { GeoMath.distanceKm(location, it.center) }
            ?: return emptyList<TariffCoefLabel>() to null
        val order = VehicleClass.configurable
        val labels = order
            .filter { it in visible }
            .map { cls ->
                val coef = nearest.coefficientFor(cls).let { c ->
                    if (c <= 0) nearest.coefficient else c
                }
                // если в зоне нет карты тарифов — достраиваем
                val resolved = if (nearest.coefficientsByClass.isEmpty()) {
                    YaRadarOfficialEngine.multiTariffCoefficients(nearest.coefficient)[cls]
                        ?: nearest.coefficient
                } else coef
                TariffCoefLabel(cls, resolved)
            }
        return labels to nearest
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocation(): Location? = runCatching {
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
            ?: client.lastLocation.await()
    }.getOrNull()
}
