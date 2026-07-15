package com.radar.coefficients.presentation.map

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.TariffCoefLabel
import com.radar.coefficients.presentation.theme.coefficientColor
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun OsmMapContent(
    center: GeoPoint,
    zoom: Double,
    zones: List<DemandZone>,
    cameraEpoch: Int,
    driverLocation: GeoPoint?,
    driverTariffLabels: List<TariffCoefLabel>,
    pinLocation: GeoPoint? = null,
    pinTariffLabels: List<TariffCoefLabel> = emptyList(),
    highlightPredictedIgnite: Boolean = true,
    minCoefForHot: Double = 1.5,
    showCoefOnCar: Boolean = true,
    showRubOnCar: Boolean = true,
    pinPlacementMode: Boolean = false,
    onZoneClick: (DemandZone) -> Unit,
    onMapClick: () -> Unit,
    onPlacePin: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(zoom)
            controller.setCenter(OsmGeoPoint(center.latitude, center.longitude))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(cameraEpoch, center.latitude, center.longitude, zoom) {
        mapView.controller.setZoom(zoom)
        mapView.controller.animateTo(OsmGeoPoint(center.latitude, center.longitude))
    }

    LaunchedEffect(
        zones, driverLocation, driverTariffLabels,
        pinLocation, pinTariffLabels,
        highlightPredictedIgnite, showCoefOnCar, showRubOnCar,
        pinPlacementMode, onZoneClick, onPlacePin
    ) {
        mapView.overlays.clear()
        mapView.overlays.add(
            MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: OsmGeoPoint?): Boolean {
                    if (p == null) return false
                    if (pinPlacementMode) {
                        onPlacePin(GeoPoint(p.latitude, p.longitude))
                        return true
                    }
                    onMapClick()
                    return false
                }
                override fun longPressHelper(p: OsmGeoPoint?): Boolean {
                    if (p == null) return false
                    // Долгое нажатие — поставить метку с кэфом + ₽
                    onPlacePin(GeoPoint(p.latitude, p.longitude))
                    return true
                }
            })
        )

        zones.forEach { zone ->
            val willIgnite = highlightPredictedIgnite && zone.willLikelyIgnite(minCoefForHot)
            val color = coefficientColor(zone.coefficient)
            val fill = AndroidColor.argb(
                if (zone.isStale() || zone.isDemo) 60 else if (willIgnite) 90 else 120,
                (color.red * 255).roundToInt(),
                (color.green * 255).roundToInt(),
                (color.blue * 255).roundToInt()
            )
            val stroke = if (willIgnite) {
                AndroidColor.argb(255, 255, 193, 7)
            } else {
                AndroidColor.argb(
                    230,
                    (color.red * 255).roundToInt(),
                    (color.green * 255).roundToInt(),
                    (color.blue * 255).roundToInt()
                )
            }

            val poly = Polygon(mapView).apply {
                if (zone.polygon.size >= 3) {
                    points = zone.polygon.map { OsmGeoPoint(it.latitude, it.longitude) }
                } else {
                    points = circlePoints(zone.center.latitude, zone.center.longitude, 900.0)
                }
                fillPaint.color = fill
                outlinePaint.color = stroke
                outlinePaint.strokeWidth = when {
                    willIgnite -> 10f
                    zone.coefficient >= 2.0 -> 8f
                    else -> 5f
                }
                if (willIgnite) {
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(18f, 12f), 0f)
                }
                setOnClickListener { _, _, _ ->
                    if (pinPlacementMode) {
                        onPlacePin(zone.center)
                    } else {
                        onZoneClick(zone)
                    }
                    true
                }
            }
            mapView.overlays.add(poly)

            val ordersHint = zone.orderStats?.let { " · ${it.ordersLastHour}/ч" }.orEmpty()
            val forecastHint = zone.forecast?.let {
                " →×${"%.1f".format(it.coefficientIn30Min)}"
            }.orEmpty()
            val rubHint = if (zone.extraIncome > 0) " +${zone.extraIncome.toInt()}₽" else ""
            val marker = Marker(mapView).apply {
                position = OsmGeoPoint(zone.center.latitude, zone.center.longitude)
                title = "×${"%.1f".format(zone.coefficient)}$rubHint$forecastHint"
                snippet = zone.districtName + ordersHint
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = makeDot(if (willIgnite) AndroidColor.parseColor("#FFC107") else stroke)
                setOnMarkerClickListener { _, _ ->
                    if (pinPlacementMode) onPlacePin(zone.center) else onZoneClick(zone)
                    true
                }
            }
            mapView.overlays.add(marker)
        }

        // Метка «сюда» (фиолетовая) — поверх зон
        pinLocation?.let { pin ->
            val pinIcon = DriverMarkerFactory.createPin(
                context = context,
                labels = pinTariffLabels,
                showCoef = showCoefOnCar,
                showRub = showRubOnCar
            )
            val pinMarker = Marker(mapView).apply {
                position = OsmGeoPoint(pin.latitude, pin.longitude)
                title = "Метка"
                snippet = pinTariffLabels.joinToString(" · ") {
                    it.mapText(showCoefOnCar, showRubOnCar)
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = pinIcon
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> true }
            }
            mapView.overlays.add(pinMarker)
        }

        // Машинка ВСЕГДА: позиция водителя или центр карты
        val carPos = driverLocation ?: center
        val carIcon = DriverMarkerFactory.create(
            context = context,
            labels = driverTariffLabels,
            showCoef = showCoefOnCar,
            showRub = showRubOnCar
        )
        val driverMarker = Marker(mapView).apply {
            position = OsmGeoPoint(carPos.latitude, carPos.longitude)
            title = "Вы здесь"
            snippet = driverTariffLabels.joinToString(" · ") {
                it.mapText(showCoefOnCar, showRubOnCar)
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = carIcon
            setInfoWindow(null)
            isFlat = false
            setOnMarkerClickListener { _, _ -> true }
        }
        mapView.overlays.add(driverMarker)
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize(),
        update = { mv -> mv.invalidate() }
    )
}

private fun makeDot(color: Int) =
    ShapeDrawable(OvalShape()).apply {
        intrinsicWidth = 48
        intrinsicHeight = 48
        paint.color = color
        paint.style = Paint.Style.FILL
        setBounds(0, 0, 48, 48)
    }

private fun circlePoints(lat: Double, lon: Double, radiusM: Double, steps: Int = 36): List<OsmGeoPoint> {
    val rLat = radiusM / 111_320.0
    val rLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.2))
    return (0 until steps).map { i ->
        val a = 2.0 * Math.PI * i / steps
        OsmGeoPoint(lat + rLat * sin(a), lon + rLon * cos(a))
    }
}
