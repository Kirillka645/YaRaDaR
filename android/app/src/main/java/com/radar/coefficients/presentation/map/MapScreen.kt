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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.radar.coefficients.domain.model.MapRadiusFilter
import com.radar.coefficients.domain.util.DataStatusLabels
import com.radar.coefficients.presentation.common.UiMessage
import com.radar.coefficients.presentation.components.CoefficientBadge
import com.radar.coefficients.presentation.components.DisclaimerBanner
import com.radar.coefficients.presentation.components.SourceMetaRow
import com.radar.coefficients.presentation.components.StatePanel
import com.radar.coefficients.presentation.theme.TouchTargetMin
import com.radar.coefficients.presentation.theme.coefficientColor
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

    val cityCenter = state.city?.center
    val start = state.driverLocation ?: cityCenter
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(start?.latitude ?: 55.75, start?.longitude ?: 37.62),
            11f
        )
    }

    LaunchedEffect(state.city?.id, state.driverLocation) {
        val target = state.driverLocation ?: state.city?.center ?: return@LaunchedEffect
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), 11.5f)
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = state.driverLocation != null,
                isTrafficEnabled = state.showTraffic
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true
            ),
            onMapClick = { viewModel.selectZone(null) }
        ) {
            state.filteredZones.forEach { zone ->
                val color = coefficientColor(zone.coefficient)
                val alpha = if (zone.isStale() || zone.isDemo) 0.28f else 0.45f
                val points = zone.polygon.map { LatLng(it.latitude, it.longitude) }
                if (points.size >= 3) {
                    Polygon(
                        points = points,
                        fillColor = color.copy(alpha = alpha),
                        strokeColor = color.copy(alpha = 0.95f),
                        strokeWidth = if (zone.coefficient >= 2.0) 8f else 5f,
                        clickable = true,
                        onClick = { viewModel.selectZone(zone) }
                    )
                } else {
                    Circle(
                        center = LatLng(zone.center.latitude, zone.center.longitude),
                        radius = 900.0,
                        fillColor = color.copy(alpha = alpha),
                        strokeColor = color,
                        clickable = true,
                        onClick = { viewModel.selectZone(zone) }
                    )
                }
                Marker(
                    state = MarkerState(LatLng(zone.center.latitude, zone.center.longitude)),
                    title = "×${zone.coefficient}",
                    snippet = zone.districtName,
                    onClick = {
                        viewModel.selectZone(zone)
                        true
                    }
                )
            }
        }

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
                        Text("Пробки", fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.showTraffic,
                            onCheckedChange = viewModel::toggleTraffic
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
                    state.driverLocation?.let {
                        // camera handled by LaunchedEffect
                    }
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
                            val diffMin = fare.range.min - base.range.max
                            val diffMax = fare.range.max - base.range.min
                            Text("Расчётная разница: +${diffMin.toInt().coerceAtLeast(0)}–${diffMax.toInt().coerceAtLeast(0)} ${fare.range.currencyCode}")
                        }
                        Text(fare.disclaimerRu, style = MaterialTheme.typography.bodySmall)
                    }
                    zone.survivalProbability?.let {
                        Text("Вероятность сохранения зоны: ${(it * 100).toInt()}%")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        DataStatusLabels.COEF_MAY_CHANGE,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    state.selectedScore?.calculationBreakdown?.let { lines ->
                        Spacer(Modifier.height(12.dp))
                        Text("Как рассчитано", fontWeight = FontWeight.Bold)
                        lines.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val uri = Uri.parse(
                                "google.navigation:q=${zone.center.latitude},${zone.center.longitude}"
                            )
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.google.android.apps.maps")
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(
                                            "https://www.google.com/maps/dir/?api=1&destination=${zone.center.latitude},${zone.center.longitude}"
                                        )
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TouchTargetMin)
                    ) {
                        Text("Построить маршрут", fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        state.pendingCitySwitch?.let { pending ->
            AlertDialog(
                onDismissRequest = { viewModel.confirmCitySwitch(false) },
                title = { Text("Сменить город?") },
                text = {
                    Text("Похоже, вы в городе ${pending.name}. Переключить зоны и тарифы?")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmCitySwitch(true) }) {
                        Text("Да")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.confirmCitySwitch(false) }) {
                        Text("Нет")
                    }
                }
            )
        }
    }
}
