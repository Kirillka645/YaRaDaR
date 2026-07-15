package com.radar.coefficients.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.radar.coefficients.data.overlay.FloatingCoefService
import com.radar.coefficients.domain.model.AlertThresholdMode
import com.radar.coefficients.domain.model.DisplayCurrency
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.domain.util.MoneyFormatter
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

    fun setOverlayEnabled(enabled: Boolean, canDraw: Boolean, startService: () -> Unit, stopService: () -> Unit) {
        viewModelScope.launch {
            if (enabled && !canDraw) {
                // permission will be requested by UI; don't enable yet
                return@launch
            }
            settingsRepository.updateSettings { it.copy(overlayEnabled = enabled) }
            if (enabled) startService() else stopService()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currency = settings.displayCurrency
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canDrawOverlays by remember {
        mutableStateOf(FloatingCoefService.canDrawOverlays(context))
    }
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawOverlays = FloatingCoefService.canDrawOverlays(context)
                // если уже включено в настройках и есть permission — поднять сервис
                if (settings.overlayEnabled && canDrawOverlays) {
                    FloatingCoefService.start(context)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Настройки", fontWeight = FontWeight.Bold) })
        DisclaimerBanner()
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Section("Валюта (суммы на экране)")
            Text(
                "По умолчанию — рубли ₽. Можно сменить; конвертация ориентировочная.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    DisplayCurrency.RUB,
                    DisplayCurrency.KZT,
                    DisplayCurrency.BYN,
                    DisplayCurrency.USD,
                    DisplayCurrency.EUR,
                    DisplayCurrency.AUTO
                ).forEach { cur ->
                    FilterChip(
                        selected = currency == cur,
                        onClick = { viewModel.update { it.copy(displayCurrency = cur) } },
                        label = {
                            Text(
                                when (cur) {
                                    DisplayCurrency.RUB -> "₽ Рубли"
                                    DisplayCurrency.KZT -> "₸ Тенге"
                                    DisplayCurrency.BYN -> "Br BYN"
                                    DisplayCurrency.USD -> "$ USD"
                                    DisplayCurrency.EUR -> "€ EUR"
                                    DisplayCurrency.AUTO -> "Как в городе"
                                    else -> cur.titleRu
                                },
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }
            Text(
                "Пример: ${MoneyFormatter.format(1250.0, currency)} · " +
                    MoneyFormatter.format(350.0, currency, withPlus = true),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(16.dp))
            Section("Порог «горячей» зоны")
            Text(
                "Не только ×1.3, а например +50 ₽ прибавки. Так же для фильтра «только горячие» и уведомлений.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.alertThresholdMode == AlertThresholdMode.COEFFICIENT,
                    onClick = {
                        viewModel.update { it.copy(alertThresholdMode = AlertThresholdMode.COEFFICIENT) }
                    },
                    label = { Text("Только кэф") }
                )
                FilterChip(
                    selected = settings.alertThresholdMode == AlertThresholdMode.RUBLES,
                    onClick = {
                        viewModel.update { it.copy(alertThresholdMode = AlertThresholdMode.RUBLES) }
                    },
                    label = { Text("Только ₽") }
                )
                FilterChip(
                    selected = settings.alertThresholdMode == AlertThresholdMode.BOTH,
                    onClick = {
                        viewModel.update { it.copy(alertThresholdMode = AlertThresholdMode.BOTH) }
                    },
                    label = { Text("Кэф + ₽") }
                )
            }
            if (settings.alertThresholdMode != AlertThresholdMode.RUBLES) {
                Text(
                    "Мин. коэффициент: ×${"%.1f".format(settings.minCoefficientAlert)}",
                    fontSize = 18.sp
                )
                Slider(
                    value = settings.minCoefficientAlert.toFloat(),
                    onValueChange = { v ->
                        viewModel.update { it.copy(minCoefficientAlert = (v * 10).toInt() / 10.0) }
                    },
                    valueRange = 1.1f..3.0f,
                    steps = 18
                )
            }
            if (settings.alertThresholdMode != AlertThresholdMode.COEFFICIENT) {
                Text(
                    "Мин. прибавка: +${settings.minExtraIncomeRub.toInt()} ₽",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Slider(
                    value = settings.minExtraIncomeRub.toFloat(),
                    onValueChange = { v ->
                        // шаг 10 ₽: 0…500
                        val rounded = ((v / 10f).toInt() * 10).toDouble()
                        viewModel.update { it.copy(minExtraIncomeRub = rounded) }
                    },
                    valueRange = 0f..500f,
                    steps = 49
                )
                Text(
                    "Пример: зона «горячая», если доп. доход ≥ +${settings.minExtraIncomeRub.toInt()} ₽",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))
            Section("Уведомления")
            RowItem("Включить оповещения") {
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(notificationsEnabled = v) } }
                )
            }
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
                onValueChange = { v ->
                    viewModel.update { it.copy(refreshIntervalMinutes = v.toInt()) }
                },
                valueRange = 1f..30f,
                steps = 28
            )

            Spacer(Modifier.height(16.dp))
            Section("Фишки карты")
            RowItem("Не гасить экран") {
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { v -> viewModel.update { it.copy(keepScreenOn = v) } }
                )
            }
            RowItem("Только горячие зоны") {
                Switch(
                    checked = settings.showOnlyHotZones,
                    onCheckedChange = { v -> viewModel.update { it.copy(showOnlyHotZones = v) } }
                )
            }
            Text(
                hotZonesHint(settings),
                style = MaterialTheme.typography.bodySmall
            )
            RowItem("Автообновление зон") {
                Switch(
                    checked = settings.autoRefreshEnabled,
                    onCheckedChange = { v -> viewModel.update { it.copy(autoRefreshEnabled = v) } }
                )
            }
            RowItem("Суммы на карте") {
                Switch(
                    checked = settings.showMoneyOnMap,
                    onCheckedChange = { v -> viewModel.update { it.copy(showMoneyOnMap = v) } }
                )
            }
            RowItem("Компактная подпись") {
                Switch(
                    checked = settings.compactDriverBubble,
                    onCheckedChange = { v -> viewModel.update { it.copy(compactDriverBubble = v) } }
                )
            }
            RowItem("Прогноз и заказы") {
                Switch(
                    checked = settings.showForecastAndOrders,
                    onCheckedChange = { v -> viewModel.update { it.copy(showForecastAndOrders = v) } }
                )
            }
            Text(
                "Где «загорится» кэф + заказы по району (модель, не Яндекс)",
                style = MaterialTheme.typography.bodySmall
            )
            RowItem("Подсветка прогноза") {
                Switch(
                    checked = settings.highlightPredictedIgnite,
                    onCheckedChange = { v ->
                        viewModel.update { it.copy(highlightPredictedIgnite = v) }
                    }
                )
            }
            Text(
                "Жёлтый пунктир — зоны, где кэф может вырасти",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(16.dp))
            Section("Поверх окон (оверлей)")
            Text(
                "Плавающий виджет поверх Яндекс Про / навигатора: кэф + прибавка ₽ в вашей точке. " +
                    "Можно перетаскивать. Тарифы — те же, что «над машинкой».",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            if (!canDrawOverlays) {
                Text(
                    "Нужно разрешение «Поверх других приложений».",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = {
                        val intent = Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text("Выдать разрешение")
                }
            }
            RowItem("Показывать оверлей") {
                Switch(
                    checked = settings.overlayEnabled && canDrawOverlays,
                    onCheckedChange = { on ->
                        if (on && !canDrawOverlays) {
                            val intent = Intent(
                                AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            viewModel.setOverlayEnabled(
                                enabled = on,
                                canDraw = canDrawOverlays,
                                startService = { FloatingCoefService.start(context) },
                                stopService = { FloatingCoefService.stop(context) }
                            )
                        }
                    }
                )
            }
            if (settings.overlayEnabled && canDrawOverlays) {
                RowItem("Кэф в оверлее") {
                    Switch(
                        checked = settings.overlayShowCoef,
                        onCheckedChange = { v ->
                            viewModel.update {
                                // хотя бы одно из двух
                                val rub = if (!v && !it.overlayShowRub) true else it.overlayShowRub
                                it.copy(overlayShowCoef = v, overlayShowRub = rub)
                            }
                        }
                    )
                }
                RowItem("Прибавка ₽") {
                    Switch(
                        checked = settings.overlayShowRub,
                        onCheckedChange = { v ->
                            viewModel.update {
                                val coef = if (!v && !it.overlayShowCoef) true else it.overlayShowCoef
                                it.copy(overlayShowRub = v, overlayShowCoef = coef)
                            }
                        }
                    )
                }
                RowItem("Компактный (1 строка)") {
                    Switch(
                        checked = settings.overlayCompact,
                        onCheckedChange = { v -> viewModel.update { it.copy(overlayCompact = v) } }
                    )
                }
                Text(
                    "Прозрачность: ${settings.overlayOpacityPercent}%",
                    fontSize = 18.sp
                )
                Slider(
                    value = settings.overlayOpacityPercent.toFloat(),
                    onValueChange = { v ->
                        viewModel.update { it.copy(overlayOpacityPercent = v.toInt().coerceIn(30, 100)) }
                    },
                    valueRange = 30f..100f,
                    steps = 13
                )
                Text("Размер оверлея", fontSize = 18.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "Малый", 1 to "Обычный", 2 to "Крупный").forEach { (sz, label) ->
                        FilterChip(
                            selected = settings.overlaySize == sz,
                            onClick = { viewModel.update { it.copy(overlaySize = sz) } },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    "Превью: Э ×1.5" + if (settings.overlayShowRub) " +180 ₽" else "",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = { FloatingCoefService.start(context) }) {
                    Text("Перезапустить оверлей")
                }
            }

            Spacer(Modifier.height(16.dp))
            Section("Смена")
            val shiftOn = settings.shiftStartedAtEpochMs > 0
            RowItem(if (shiftOn) "Смена идёт" else "Начать смену") {
                Switch(
                    checked = shiftOn,
                    onCheckedChange = { on ->
                        viewModel.update {
                            if (on) it.copy(
                                shiftStartedAtEpochMs = System.currentTimeMillis(),
                                shiftZonesChecked = 0
                            ) else it.copy(shiftStartedAtEpochMs = 0L, shiftZonesChecked = 0)
                        }
                    }
                )
            }
            if (shiftOn) {
                val mins = ((System.currentTimeMillis() - settings.shiftStartedAtEpochMs) / 60_000).toInt()
                Text(
                    "Длительность: ~$mins мин · зон просмотрено: ${settings.shiftZonesChecked}",
                    fontWeight = FontWeight.SemiBold
                )
            }

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
                else "Работает YaRaDaR Official API (встроенный движок)",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(16.dp))
            Section("Тарифы над машинкой")
            Text(
                "Э, К, Д… — что гореть над вашей позицией",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            // Пресеты
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.mapVisibleTariffs == setOf(
                        VehicleClass.ECONOMY, VehicleClass.COMFORT, VehicleClass.CHILD
                    ),
                    onClick = {
                        viewModel.update {
                            it.copy(
                                mapVisibleTariffs = setOf(
                                    VehicleClass.ECONOMY,
                                    VehicleClass.COMFORT,
                                    VehicleClass.CHILD
                                )
                            )
                        }
                    },
                    label = { Text("Э+К+Д") }
                )
                FilterChip(
                    selected = settings.mapVisibleTariffs.size == VehicleClass.configurable.size,
                    onClick = {
                        viewModel.update {
                            it.copy(mapVisibleTariffs = VehicleClass.configurable.toSet())
                        }
                    },
                    label = { Text("Все тарифы") }
                )
                FilterChip(
                    selected = settings.mapVisibleTariffs == setOf(VehicleClass.ECONOMY),
                    onClick = {
                        viewModel.update { it.copy(mapVisibleTariffs = setOf(VehicleClass.ECONOMY)) }
                    },
                    label = { Text("Только Э") }
                )
            }
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
            Section("Расчёт выгоды (${currency.symbol})")
            Text(
                "Топливо: ${MoneyFormatter.format(settings.fuelCostPerKm, currency)} / км",
                fontSize = 18.sp
            )
            Slider(
                value = settings.fuelCostPerKm.toFloat(),
                onValueChange = { v -> viewModel.update { it.copy(fuelCostPerKm = v.toDouble()) } },
                valueRange = 0f..30f
            )
            Text(
                "Время: ${MoneyFormatter.format(settings.timeCostPerMinute, currency)} / мин",
                fontSize = 18.sp
            )
            Slider(
                value = settings.timeCostPerMinute.toFloat(),
                onValueChange = { v ->
                    viewModel.update { it.copy(timeCostPerMinute = v.toDouble()) }
                },
                valueRange = 0f..30f
            )

            Spacer(Modifier.height(24.dp))
            Text(
                "Все суммы приблизительные. Приложение не гарантирует доход и не является " +
                    "официальным продуктом Яндекса.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun hotZonesHint(settings: UserSettings): String = when (settings.alertThresholdMode) {
    AlertThresholdMode.COEFFICIENT ->
        "Скрывать зоны с кэфом ниже ×${"%.1f".format(settings.minCoefficientAlert)}"
    AlertThresholdMode.RUBLES ->
        "Скрывать зоны с прибавкой ниже +${settings.minExtraIncomeRub.toInt()} ₽"
    AlertThresholdMode.BOTH ->
        "Скрывать, если и кэф < ×${"%.1f".format(settings.minCoefficientAlert)} " +
            "и прибавка < +${settings.minExtraIncomeRub.toInt()} ₽"
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
