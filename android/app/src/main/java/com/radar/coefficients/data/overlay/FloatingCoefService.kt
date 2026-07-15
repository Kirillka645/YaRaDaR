package com.radar.coefficients.data.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.radar.coefficients.MainActivity
import com.radar.coefficients.R
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.repository.CityRepository
import com.radar.coefficients.domain.repository.DemandRepository
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.usecase.LocalCoefResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Плавающий виджет поверх других окон: кэф + прибавка ₽ в точке водителя.
 * Требует SYSTEM_ALERT_WINDOW. Работает как foreground service.
 */
@AndroidEntryPoint
class FloatingCoefService : Service() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var demandRepository: DemandRepository
    @Inject lateinit var cityRepository: CityRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var windowManager: WindowManager? = null
    private var bubbleRoot: LinearLayout? = null
    private var titleView: TextView? = null
    private var bodyView: TextView? = null
    private var districtView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var settings: UserSettings = UserSettings()
    private var driverPoint: GeoPoint? = null
    private var refreshJob: Job? = null

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onLocation(loc)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createBubble()
        startLocationUpdates()
        scope.launch {
            settingsRepository.observeSettings().collectLatest { s ->
                settings = s
                if (!s.overlayEnabled) {
                    stopSelf()
                    return@collectLatest
                }
                applyStyle()
                refreshSnapshot()
            }
        }
        refreshJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshSnapshot()
                val mins = settings.refreshIntervalMinutes.coerceIn(1, 30)
                delay(mins * 60_000L)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch {
                    settingsRepository.updateSettings { it.copy(overlayEnabled = false) }
                    stopSelf()
                }
            }
            ACTION_REFRESH -> scope.launch { refreshSnapshot() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        refreshJob?.cancel()
        runCatching { fused.removeLocationUpdates(locationCallback) }
        removeBubble()
        scope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val channelId = "overlay_live"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Оверлей кэфа",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Плавающий кэф поверх других окон"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingCoefService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("YaRaDaR — оверлей")
            .setContentText("Кэф и ₽ на вашей точке поверх окон")
            .setContentIntent(open)
            .addAction(0, "Стоп", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, n)
    }

    private fun createBubble() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            elevation = 12f
        }
        val title = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            text = "Э ×1.0 +0 ₽"
        }
        val body = TextView(this).apply {
            setTextColor(0xFFE3F2FD.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = ""
            visibility = View.GONE
        }
        val district = TextView(this).apply {
            setTextColor(0xB3FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "YaRaDaR"
        }
        root.addView(title)
        root.addView(body)
        root.addView(district)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        // drag
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        root.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX
                    downY = ev.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (ev.rawX - downX).toInt()
                    lp.y = startY + (ev.rawY - downY).toInt()
                    windowManager?.updateViewLayout(root, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // короткий тап — открыть приложение
                    val moved = kotlin.math.abs(ev.rawX - downX) + kotlin.math.abs(ev.rawY - downY)
                    if (moved < 12f * density) {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    true
                }
                else -> false
            }
        }

        bubbleRoot = root
        titleView = title
        bodyView = body
        districtView = district
        layoutParams = lp
        applyStyle()
        windowManager?.addView(root, lp)
    }

    private fun applyStyle() {
        val root = bubbleRoot ?: return
        val density = resources.displayMetrics.density
        val alpha = (settings.overlayOpacityPercent.coerceIn(30, 100) * 255 / 100)
        val bg = GradientDrawable().apply {
            cornerRadius = 16f * density
            setColor(Color.argb(alpha, 13, 71, 161))
            setStroke((2 * density).toInt(), Color.WHITE)
        }
        root.background = bg

        val size = settings.overlaySize.coerceIn(0, 2)
        val titleSp = when (size) { 0 -> 14f; 2 -> 20f; else -> 16f }
        val bodySp = when (size) { 0 -> 11f; 2 -> 15f; else -> 13f }
        titleView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, titleSp)
        bodyView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySp)
        districtView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, bodySp - 2f)

        val pad = when (size) { 0 -> 8; 2 -> 14; else -> 12 }
        root.setPadding(
            (pad * density).toInt(),
            ((pad - 2) * density).toInt(),
            (pad * density).toInt(),
            ((pad - 2) * density).toInt()
        )
        bodyView?.visibility = if (settings.overlayCompact) View.GONE else View.VISIBLE
    }

    private fun removeBubble() {
        val root = bubbleRoot ?: return
        runCatching { windowManager?.removeView(root) }
        bubbleRoot = null
        titleView = null
        bodyView = null
        districtView = null
        layoutParams = null
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val req = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(8_000L)
            .setMinUpdateDistanceMeters(25f)
            .build()
        fused.requestLocationUpdates(req, locationCallback, mainLooper)
        scope.launch {
            val last = runCatching {
                fused.lastLocation.await()
            }.getOrNull()
            last?.let { onLocation(it) }
        }
    }

    private fun onLocation(loc: Location) {
        driverPoint = GeoPoint(loc.latitude, loc.longitude)
        scope.launch {
            settingsRepository.updateSettings {
                it.copy(lastKnownLatitude = loc.latitude, lastKnownLongitude = loc.longitude)
            }
            refreshSnapshot()
        }
    }

    private suspend fun refreshSnapshot() {
        val point = driverPoint
            ?: settings.lastKnownLatitude?.let { lat ->
                settings.lastKnownLongitude?.let { lon -> GeoPoint(lat, lon) }
            }
        val cityId = settings.selectedCityId
        val city = cityId?.let { cityRepository.getCity(it) }
            ?: point?.let { p -> cityRepository.detectCity(p.latitude, p.longitude) }

        var zones = if (city != null) {
            demandRepository.refreshZones(city.id, null).getOrElse {
                demandRepository.getCachedZones(city.id)
            }
        } else emptyList()

        if (zones.isEmpty() && city != null) {
            zones = demandRepository.getCachedZones(city.id)
        }

        val snap = LocalCoefResolver.resolveAt(
            location = point ?: city?.center,
            zones = zones,
            visible = settings.mapVisibleTariffs
        )

        val showCoef = settings.overlayShowCoef
        val showRub = settings.overlayShowRub
        val main = snap.labels.firstOrNull()
        val title = when {
            main != null -> main.mapText(showCoef, showRub)
            showCoef && showRub -> "×${"%.1f".format(snap.blendedEconomyCoef)} +${snap.blendedExtraRub.toInt()} ₽"
            showCoef -> "×${"%.1f".format(snap.blendedEconomyCoef)}"
            showRub -> "+${snap.blendedExtraRub.toInt()} ₽"
            else -> "YaRaDaR"
        }
        val body = if (!settings.overlayCompact && snap.labels.size > 1) {
            snap.labels.drop(1).take(4).joinToString("  ") { it.mapText(showCoef, showRub) }
        } else ""
        val district = buildString {
            append(snap.districtName.take(28))
            if (point == null) append(" · нет GPS")
        }

        withContext(Dispatchers.Main) {
            titleView?.text = title
            bodyView?.text = body
            bodyView?.visibility = if (body.isNotBlank() && !settings.overlayCompact) {
                View.VISIBLE
            } else View.GONE
            districtView?.text = district
            // цвет по «жару»
            val hot = snap.blendedEconomyCoef >= settings.minCoefficientAlert ||
                snap.blendedExtraRub >= settings.minExtraIncomeRub
            val alpha = (settings.overlayOpacityPercent.coerceIn(30, 100) * 255 / 100)
            val density = resources.displayMetrics.density
            bubbleRoot?.background = GradientDrawable().apply {
                cornerRadius = 16f * density
                if (hot) setColor(Color.argb(alpha, 230, 81, 0))
                else setColor(Color.argb(alpha, 13, 71, 161))
                setStroke((2 * density).toInt(), Color.WHITE)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    companion object {
        const val ACTION_STOP = "com.radar.coefficients.overlay.STOP"
        const val ACTION_REFRESH = "com.radar.coefficients.overlay.REFRESH"
        private const val NOTIF_ID = 2101

        fun start(context: Context) {
            val i = Intent(context, FloatingCoefService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingCoefService::class.java))
        }

        fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
