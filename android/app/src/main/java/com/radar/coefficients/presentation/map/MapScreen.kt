package com.radar.coefficients.presentation.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radar.coefficients.domain.model.AlertThresholdMode
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.MapRadiusFilter
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.domain.util.MoneyFormatter
import com.radar.coefficients.presentation.common.UiMessage
import com.radar.coefficients.presentation.components.CoefficientBadge
import com.radar.coefficients.presentation.components.DisclaimerBanner
import com.radar.coefficients.presentation.components.SourceMetaRow
import com.radar.coefficients.presentation.components.StatePanel
import com.radar.coefficients.presentation.theme.TouchTargetMin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    onOpenCityPicker: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var cameraEpoch by remember { mutableIntStateOf(0) }
    val currency = state.settings.displayCurrency
    val cityCur = state.city?.currencyCode ?: "RUB"

    // Не гасить экран
    DisposableEffect(state.settings.keepScreenOn) {
        val holder = view.keepScreenOn
        view.keepScreenOn = state.settings.keepScreenOn
        onDispose { view.keepScreenOn = holder }
    }

    // Автообновление
    LaunchedEffect(state.settings.autoRefreshEnabled, state.settings.refreshIntervalMinutes, state.city?.id) {
        if (!state.settings.autoRefreshEnabled) return@LaunchedEffect
        val period = state.settings.refreshIntervalMinutes.coerceAtLeast(1) * 60_000L
        while (true) {
            delay(period)
            viewModel.refresh()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) viewModel.bootstrapLocation()
        else viewModel.refresh()
    }

    LaunchedEffect(Unit) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            viewModel.bootstrapLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(state.city?.id, state.driverLocation) {
        cameraEpoch++
    }

    val mapCenter = state.driverLocation
        ?: state.city?.center
        ?: GeoPoint(57.7679, 40.9269) // Кострома
    val showCoefOnCar = when (state.settings.alertThresholdMode) {
        AlertThresholdMode.RUBLES -> false
        AlertThresholdMode.COEFFICIENT -> true
        AlertThresholdMode.BOTH -> true
    }
    val showRubOnCar = when (state.settings.alertThresholdMode) {
        AlertThresholdMode.COEFFICIENT -> state.settings.showMoneyOnMap
        AlertThresholdMode.RUBLES -> true
        AlertThresholdMode.BOTH -> true
    }
    var pinPlacementMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap — без API-ключа, карты видны сразу
        OsmMapContent(
            center = mapCenter,
            zoom = 12.0,
            zones = state.filteredZones,
            cameraEpoch = cameraEpoch,
            driverLocation = state.driverLocation ?: state.city?.center ?: mapCenter,
            driverTariffLabels = state.driverTariffLabels,
            pinLocation = state.pinLocation,
            pinTariffLabels = state.pinTariffLabels,
            highlightPredictedIgnite = state.settings.highlightPredictedIgnite,
            minCoefForHot = state.settings.minCoefficientAlert,
            showCoefOnCar = showCoefOnCar,
            showRubOnCar = showRubOnCar,
            pinPlacementMode = pinPlacementMode,
            onZoneClick = { viewModel.selectZone(it) },
            onMapClick = { viewModel.selectZone(null) },
            onPlacePin = { point ->
                viewModel.placePin(point)
                pinPlacementMode = false
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            DisclaimerBanner()
            Spacer(Modifier.height(8.dp))
            Surface(
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.city?.name ?: "Город не выбран",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Text(
                                text = buildString {
                                    append(state.city?.region.orEmpty())
                                    if (state.city != null) append(" · ${state.city!!.country.name}")
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Карта: OpenStreetMap · маршрут: Яндекс Карты",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (state.driverTariffLabels.isNotEmpty()) {
                                Text(
                                    text = "Здесь: " + state.driverTariffLabels.joinToString(" · ") {
                                        it.mapText(showCoefOnCar, showRubOnCar)
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            if (state.settings.showMoneyOnMap &&
                                (state.driverTariffLabels.isNotEmpty() || state.localZone != null)
                            ) {
                                val extraHere = state.driverTariffLabels
                                    .firstOrNull { it.vehicleClass == VehicleClass.ECONOMY }
                                    ?.extraRub
                                    ?: state.driverTariffLabels.firstOrNull()?.extraRub
                                    ?: state.localZone?.extraIncome
                                    ?: 0.0
                                Text(
                                    text = "Ориент. доп. доход: " + MoneyFormatter.format(
                                        extraHere,
                                        currency,
                                        cityCur,
                                        cityCur
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        TextButton(onClick = onOpenCityPicker) {
                            Text("Сменить", fontSize = 16.sp)
                        }
                    }
                    val updated = state.lastUpdatedAt?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: "—"
                    Text(
                        "Обновлено: $updated · валюта ${MoneyFormatter.resolveDisplay(currency, cityCur).symbol}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    if (state.settings.shiftStartedAtEpochMs > 0) {
                        val mins = ((System.currentTimeMillis() - state.settings.shiftStartedAtEpochMs) / 60_000).toInt()
                        Text(
                            "Смена: $mins мин · проверок: ${state.settings.shiftZonesChecked}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!state.realDataAvailable) {
                        Text(
                            DataStatusLabels.REAL_UNAVAILABLE,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapRadiusFilter.entries.forEach { r ->
                            FilterChip(
                                selected = state.radius == r,
                                onClick = { viewModel.setRadius(r) },
                                label = { Text(r.labelRu, fontSize = 15.sp) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Слой пробок (в OSM недоступен)",
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.showTraffic,
                            onCheckedChange = viewModel::toggleTraffic,
                            enabled = false
                        )
                    }
                    Text(
                        if (pinPlacementMode) "Режим метки: тапните на карту"
                        else "Долгое нажатие / 📍 — метка с кэфом + ₽",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pinPlacementMode) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (pinPlacementMode) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    tonalElevation = 4.dp,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Тапните в любое место на карте — поставим метку с кэфом и прибавкой ₽",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (state.message != null && state.message != UiMessage.Loading && state.filteredZones.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                StatePanel(message = state.message!!, onRetry = viewModel::refresh)
            }
        }

        // Карточка метки
        if (state.pinLocation != null) {
            Surface(
                tonalElevation = 8.dp,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 88.dp, bottom = 16.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Метка",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearPin) {
                            Text("Убрать")
                        }
                    }
                    state.pinDistrictName?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "×${"%.1f".format(state.pinEconomyCoef)} · +" +
                            "${state.pinExtraRub.toInt()} ₽ (ориент.)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (state.pinTariffLabels.isNotEmpty()) {
                        Text(
                            state.pinTariffLabels.joinToString(" · ") {
                                it.mapText(showCoefOnCar, showRubOnCar)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "Модель YaRaDaR · не Яндекс Про",
                        style = MaterialTheme.typography.labelSmall
                    )
                    val pin = state.pinLocation!!
                    Button(
                        onClick = {
                            openYandexRoute(context, pin.latitude, pin.longitude)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text("Маршрут к метке (Яндекс)")
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.pinLocation != null) {
                FloatingActionButton(
                    onClick = viewModel::clearPin,
                    modifier = Modifier.size(TouchTargetMin),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Убрать метку")
                }
            }
            FloatingActionButton(
                onClick = { pinPlacementMode = !pinPlacementMode },
                modifier = Modifier.size(TouchTargetMin),
                shape = CircleShape,
                containerColor = if (pinPlacementMode) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ) {
                Icon(
                    Icons.Default.Place,
                    contentDescription = "Поставить метку"
                )
            }
            FloatingActionButton(
                onClick = viewModel::refresh,
                modifier = Modifier.size(TouchTargetMin),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
            }
            FloatingActionButton(
                onClick = {
                    viewModel.centerOnDriver()
                    cameraEpoch++
                },
                modifier = Modifier.size(TouchTargetMin),
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Моё местоположение")
            }
        }

        if (state.selectedZone != null) {
            val zone = state.selectedZone!!
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { viewModel.selectZone(null) },
                sheetState = sheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(zone.districtName, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                    Spacer(Modifier.height(8.dp))
                    CoefficientBadge(zone.coefficient, large = true)
                    Spacer(Modifier.height(12.dp))
                    SourceMetaRow(zone)
                    Spacer(Modifier.height(12.dp))
                    // Кэфы по тарифам в зоне
                    if (zone.coefficientsByClass.isNotEmpty()) {
                        Text("Кэфы по тарифам", fontWeight = FontWeight.SemiBold)
                        Text(
                            zone.coefficientsByClass.entries
                                .sortedBy { it.key.ordinal }
                                .joinToString(" · ") { "${it.key.shortLabel} ×${"%.1f".format(it.value)}" }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text("Жар: ${zone.heatScore}/100", fontWeight = FontWeight.SemiBold)
                    if (state.settings.showForecastAndOrders) {
                        zone.orderStats?.let { o ->
                            Text(
                                "Заказы в районе: ~${o.ordersLast15Min} за 15 мин · " +
                                    "~${o.ordersLastHour}/ч · сегодня ~${o.ordersToday}"
                            )
                            Text("Пик обычно около ${o.peakHourLocal}:00 (модель)")
                        }
                        zone.forecast?.let { f ->
                            Spacer(Modifier.height(6.dp))
                            Text("Прогноз кэфа (модель)", fontWeight = FontWeight.Bold)
                            Text(
                                "15 мин → ×${"%.1f".format(f.coefficientIn15Min)} · " +
                                    "30 мин → ×${"%.1f".format(f.coefficientIn30Min)} · " +
                                    "60 мин → ×${"%.1f".format(f.coefficientIn60Min)}"
                            )
                            Text(f.summaryRu, color = MaterialTheme.colorScheme.primary)
                            f.minutesToIgnite?.let { m ->
                                if (m > 0) {
                                    Text(
                                        "Ожидается «зажжение» через ~$m мин " +
                                            "(вероятность ${(f.igniteProbability30Min * 100).toInt()}%)",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Text(
                                "Прогноз ориентировочный, не данные Яндекса",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    state.selectedScore?.let { score ->
                        Text("До зоны: ${"%.1f".format(score.distanceKm)} км · ${score.directionLabelRu}")
                        Text(
                            "В пути: ~${score.travelTimeWithTrafficMinutes ?: score.travelTimeMinutes} мин" +
                                if (score.travelTimeWithTrafficMinutes == null) " (без пробок)" else ""
                        )
                        val travelMin = (score.travelTimeWithTrafficMinutes ?: score.travelTimeMinutes).coerceAtLeast(1)
                        val hourly = score.expectedNetBenefit * (60.0 / travelMin)
                        Text(
                            "Ориент. выгода: " + MoneyFormatter.format(
                                score.expectedNetBenefit, currency, cityCur, cityCur, withPlus = true
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Доп. доход: " + MoneyFormatter.format(
                                score.expectedGrossExtra, currency, cityCur, cityCur
                            )
                        )
                        Text(
                            "Темп: " + MoneyFormatter.formatPerHour(hourly, currency, cityCur, cityCur),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "Топливо: " + MoneyFormatter.format(score.fuelCost, currency, cityCur, cityCur) +
                                " · время: " + MoneyFormatter.format(score.timeCost, currency, cityCur, cityCur)
                        )
                    }
                    state.selectedFare?.let { fare ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Примерная цена: " + MoneyFormatter.formatRange(fare.range, currency, cityCur),
                            fontWeight = FontWeight.SemiBold
                        )
                        state.selectedFareBase?.let { base ->
                            Text(
                                "Без коэффициента: " +
                                    MoneyFormatter.formatRange(base.range, currency, cityCur)
                            )
                        }
                        Text(fare.disclaimerRu, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        DataStatusLabels.COEF_MAY_CHANGE,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            openYandexRoute(context, zone.center.latitude, zone.center.longitude)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TouchTargetMin)
                    ) {
                        Text("Маршрут в Яндекс Картах", fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val clip = android.content.ClipData.newPlainText(
                                "coords",
                                "${zone.center.latitude},${zone.center.longitude}"
                            )
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(clip)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Скопировать координаты")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        state.pendingCitySwitch?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.confirmCitySwitch(false) },
                title = { Text("Сменить город?") },
                text = { Text("Похоже, вы в городе ${pending.name}. Переключить зоны и тарифы?") },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmCitySwitch(true) }) { Text("Да") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.confirmCitySwitch(false) }) { Text("Нет") }
                }
            )
        }
    }
}

private fun openYandexRoute(context: android.content.Context, lat: Double, lon: Double) {
    val yandexApp = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("yandexmaps://maps.yandex.ru/?rtext=~$lat,$lon&rtt=auto")
    )
    val yandexWeb = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://yandex.ru/maps/?rtext=~$lat%2C$lon&rtt=auto")
    )
    runCatching { context.startActivity(yandexApp) }.onFailure {
        context.startActivity(yandexWeb)
    }
}
