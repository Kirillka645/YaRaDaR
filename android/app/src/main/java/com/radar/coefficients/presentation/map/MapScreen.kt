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
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.MapRadiusFilter
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.common.UiMessage
import com.radar.coefficients.presentation.components.CoefficientBadge
import com.radar.coefficients.presentation.components.DisclaimerBanner
import com.radar.coefficients.presentation.components.SourceMetaRow
import com.radar.coefficients.presentation.components.StatePanel
import com.radar.coefficients.presentation.theme.TouchTargetMin
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MapScreen(
    onOpenCityPicker: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var cameraEpoch by remember { mutableIntStateOf(0) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        // OpenStreetMap — без API-ключа, карты видны сразу
        OsmMapContent(
            center = mapCenter,
            zoom = 12.0,
            zones = state.filteredZones,
            cameraEpoch = cameraEpoch,
            onZoneClick = { viewModel.selectZone(it) },
            onMapClick = { viewModel.selectZone(null) },
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
                        }
                        TextButton(onClick = onOpenCityPicker) {
                            Text("Сменить", fontSize = 16.sp)
                        }
                    }
                    val updated = state.lastUpdatedAt?.let {
                        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: "—"
                    Text("Обновлено: $updated", style = MaterialTheme.typography.labelLarge)
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
                }
            }
            if (state.message != null && state.message != UiMessage.Loading && state.filteredZones.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                StatePanel(message = state.message!!, onRetry = viewModel::refresh)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    state.selectedScore?.let { score ->
                        Text("До зоны: ${"%.1f".format(score.distanceKm)} км · ${score.directionLabelRu}")
                        Text(
                            "В пути: ~${score.travelTimeWithTrafficMinutes ?: score.travelTimeMinutes} мин" +
                                if (score.travelTimeWithTrafficMinutes == null) " (без пробок)" else ""
                        )
                        Text(
                            "Ориент. выгода: ${"%.0f".format(score.expectedNetBenefit)} ${state.city?.currencyCode ?: ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text("Доп. доход (ориент.): ${"%.0f".format(score.expectedGrossExtra)}")
                    }
                    state.selectedFare?.let { fare ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Примерная цена поездки: ${fare.range.min.toInt()}–${fare.range.max.toInt()} ${fare.range.currencyCode}",
                            fontWeight = FontWeight.SemiBold
                        )
                        state.selectedFareBase?.let { base ->
                            Text("Без коэффициента: ${base.range.min.toInt()}–${base.range.max.toInt()} ${base.range.currencyCode}")
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
