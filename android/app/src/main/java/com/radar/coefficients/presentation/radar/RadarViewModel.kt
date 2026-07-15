package com.radar.coefficients.presentation.radar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.RadarSortMode
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.ZoneBenefitScore
import com.radar.coefficients.domain.repository.CityRepository
import com.radar.coefficients.domain.repository.DemandRepository
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.usecase.BenefitCalculator
import com.radar.coefficients.presentation.common.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RadarUiState(
    val isLoading: Boolean = true,
    val scores: List<ZoneBenefitScore> = emptyList(),
    val sortMode: RadarSortMode = RadarSortMode.MAX_BENEFIT,
    val city: City? = null,
    val settings: UserSettings = UserSettings(),
    val message: UiMessage? = null
)

@HiltViewModel
class RadarViewModel @Inject constructor(
    private val demandRepository: DemandRepository,
    private val cityRepository: CityRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RadarUiState())
    val state: StateFlow<RadarUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observeSettings(),
                cityRepository.observeSelectedCity()
            ) { s, c -> s to c }.collect { (settings, city) ->
                _state.update { it.copy(settings = settings, city = city) }
                reload()
            }
        }
    }

    fun setSort(mode: RadarSortMode) {
        _state.update { it.copy(sortMode = mode) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val city = _state.value.city ?: return@launch
            _state.update { it.copy(isLoading = true, message = UiMessage.Loading) }
            demandRepository.refreshZones(city.id, null)
            val zones = demandRepository.observeZones(city.id).first()
            val settings = _state.value.settings
            val driver = when {
                settings.lastKnownLatitude != null && settings.lastKnownLongitude != null ->
                    GeoPoint(settings.lastKnownLatitude!!, settings.lastKnownLongitude!!)
                else -> city.center
            }
            val scores = BenefitCalculator.rank(
                zones = zones,
                driverLocation = driver,
                settings = settings,
                sortMode = _state.value.sortMode
            )
            _state.update {
                it.copy(
                    isLoading = false,
                    scores = scores,
                    message = if (scores.isEmpty()) UiMessage.EmptyZones else null
                )
            }
        }
    }
}
