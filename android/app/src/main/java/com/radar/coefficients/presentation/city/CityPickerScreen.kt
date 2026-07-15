package com.radar.coefficients.presentation.city

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.repository.CityRepository
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.components.DisclaimerBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CityPickerState(
    val query: String = "",
    val cities: List<City> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val countries: List<String> = emptyList()
)

@HiltViewModel
class CityPickerViewModel @Inject constructor(
    private val cityRepository: CityRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _state = MutableStateFlow(CityPickerState())
    val state: StateFlow<CityPickerState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { s ->
                _state.update { it.copy(settings = s) }
            }
        }
        search("")
    }

    fun search(query: String) {
        viewModelScope.launch {
            val list = cityRepository.searchCities(query)
            _state.update { it.copy(query = query, cities = list) }
        }
    }

    fun select(city: City, onDone: () -> Unit) {
        viewModelScope.launch {
            cityRepository.setSelectedCity(city)
            onDone()
        }
    }

    fun toggleFavorite(cityId: String) {
        viewModelScope.launch {
            val fav = _state.value.settings.favoriteCityIds
            if (cityId in fav) cityRepository.removeFavorite(cityId)
            else cityRepository.addFavorite(cityId)
        }
    }

    fun detectByLocation(lat: Double, lon: Double, onDone: () -> Unit) {
        viewModelScope.launch {
            cityRepository.detectCity(lat, lon)?.let {
                cityRepository.setSelectedCity(it)
                onDone()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CityPickerScreen(
    onDone: () -> Unit,
    viewModel: CityPickerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Выбор города", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onDone) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            }
        )
        DisclaimerBanner()
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::search,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Страна, регион или город") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Город определяется автоматически по геопозиции. " +
                    "Если геолокация недоступна — выберите вручную. " +
                    "Последний город сохраняется.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        LazyColumn {
            val favorites = state.cities.filter { it.id in state.settings.favoriteCityIds }
            if (favorites.isNotEmpty()) {
                item {
                    Text(
                        "Избранные",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
                items(favorites, key = { "f-${it.id}" }) { city ->
                    CityRow(city, state.settings, viewModel, onDone)
                }
            }
            item {
                Text(
                    "Все результаты",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold
                )
            }
            items(state.cities, key = { it.id }) { city ->
                CityRow(city, state.settings, viewModel, onDone)
            }
        }
    }
}

@Composable
private fun CityRow(
    city: City,
    settings: UserSettings,
    viewModel: CityPickerViewModel,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.select(city, onDone) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(city.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${city.region}, ${city.country.name} · ${city.currencyCode}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                DataStatusLabels.cityAvailabilityRu(city.dataAvailability),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
        IconButton(onClick = { viewModel.toggleFavorite(city.id) }) {
            Icon(
                if (city.id in settings.favoriteCityIds) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "Избранное"
            )
        }
    }
}
