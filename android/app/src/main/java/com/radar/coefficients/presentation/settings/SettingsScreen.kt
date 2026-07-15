package com.radar.coefficients.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.components.DisclaimerBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val settings = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun update(block: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.updateSettings(block) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Настройки", fontWeight = FontWeight.Bold) })
        DisclaimerBanner()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Section("Уведомления")
            RowItem("Включить оповещения") {
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(notificationsEnabled = v) } }
                )
            }
            Text("Мин. коэффициент: ×${"%.1f".format(settings.minCoefficientAlert)}", fontSize = 18.sp)
            Slider(
                value = settings.minCoefficientAlert.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(minCoefficientAlert = (v * 10).toInt() / 10.0) } },
                valueRange = 1.1f..3.0f,
                steps = 18
            )
            Text("Радиус уведомлений: ${settings.notificationRadiusKm} км", fontSize = 18.sp)
            Slider(
                value = settings.notificationRadiusKm.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(notificationRadiusKm = v.toInt()) } },
                valueRange = 1f..20f,
                steps = 18
            )
            Text("Частота обновления: ${settings.refreshIntervalMinutes} мин", fontSize = 18.sp)
            Slider(
                value = settings.refreshIntervalMinutes.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(refreshIntervalMinutes = v.toInt()) } },
                valueRange = 1f..30f,
                steps = 28
            )
            Text(
                "Во время движения — только короткие уведомления, без сложных окон.",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(16.dp))
            Section("Данные")
            RowItem("Деморежим") {
                Switch(
                    checked = settings.demoModeEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(demoModeEnabled = v) } }
                )
            }
            Text(
                if (settings.demoModeEnabled) DataStatusLabels.DEMO_BANNER
                else "Демо выключено: без разрешённого источника зоны могут быть пустыми",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            Section("Тарифы над машинкой на карте")
            Text(
                "Выберите, какие кэфы показывать над вашей позицией (Э, К, Д…).",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            VehicleClass.configurable.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cls ->
                        val on = cls in settings.mapVisibleTariffs
                        FilterChip(
                            selected = on,
                            onClick = {
                                viewModel.update { s ->
                                    val next = s.mapVisibleTariffs.toMutableSet()
                                    if (cls in next) next.remove(cls) else next.add(cls)
                                    s.copy(
                                        mapVisibleTariffs = next.ifEmpty {
                                            setOf(VehicleClass.ECONOMY)
                                        }
                                    )
                                }
                            },
                            label = {
                                Text("${cls.shortLabel} · ${cls.displayNameRu}", fontSize = 14.sp)
                            },
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 8.dp)
                                .weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text(
                "Сейчас: " + settings.mapVisibleTariffs
                    .sortedBy { VehicleClass.configurable.indexOf(it) }
                    .joinToString(", ") { "${it.shortLabel} (${it.displayNameRu})" },
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(Modifier.height(16.dp))
            Section("Класс авто для расчёта цены")
            VehicleClass.configurable.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { cls ->
                        FilterChip(
                            selected = settings.selectedVehicleClass == cls,
                            onClick = { viewModel.update { it.copy(selectedVehicleClass = cls) } },
                            label = { Text("${cls.shortLabel} ${cls.displayNameRu}") },
                            modifier = Modifier
                                .padding(end = 8.dp, bottom = 8.dp)
                                .weight(1f)
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(16.dp))
            Section("Расчёт выгоды")
            Text("Стоимость топлива: ${settings.fuelCostPerKm} / км", fontSize = 18.sp)
            Slider(
                value = settings.fuelCostPerKm.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(fuelCostPerKm = v.toDouble()) } },
                valueRange = 0f..30f
            )
            Text("Стоимость времени: ${settings.timeCostPerMinute} / мин", fontSize = 18.sp)
            Slider(
                value = settings.timeCostPerMinute.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(timeCostPerMinute = v.toDouble()) } },
                valueRange = 0f..30f
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Все суммы приблизительные. Приложение не гарантирует доход и не является " +
                    "официальным продуктом Яндекса. Закрытые API и перехват трафика не используются.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun RowItem(label: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 18.sp)
        trailing()
    }
}
