package com.radar.coefficients.data.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.radar.coefficients.R
import com.radar.coefficients.domain.repository.CityRepository
import com.radar.coefficients.domain.repository.DemandRepository
import com.radar.coefficients.domain.repository.SettingsRepository
import com.radar.coefficients.domain.util.GeoMath
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Background refresh. Uses heads-up-safe notification style without complex interaction.
 */
@HiltWorker
class DemandRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val demandRepository: DemandRepository,
    private val cityRepository: CityRepository,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.getSettings()
        if (!settings.notificationsEnabled) return Result.success()
        val cityId = settings.selectedCityId ?: return Result.success()
        val refresh = demandRepository.refreshZones(cityId, null)
        if (refresh.isFailure) return Result.retry()

        val zones = refresh.getOrDefault(emptyList())
        val lat = settings.lastKnownLatitude
        val lon = settings.lastKnownLongitude
        if (lat == null || lon == null) return Result.success()

        val driver = com.radar.coefficients.domain.model.GeoPoint(lat, lon)
        val hit = zones.firstOrNull { zone ->
            zone.coefficient >= settings.minCoefficientAlert &&
                GeoMath.distanceKm(driver, zone.center) <= settings.notificationRadiusKm &&
                !zone.isDemo
        } ?: zones.firstOrNull { zone ->
            // In demo mode still notify but mark as demo
            settings.demoModeEnabled &&
                zone.coefficient >= settings.minCoefficientAlert &&
                GeoMath.distanceKm(driver, zone.center) <= settings.notificationRadiusKm
        }

        if (hit != null) {
            showSimpleNotification(hit.districtName, hit.coefficient, hit.isDemo)
        }
        return Result.success()
    }

    private fun showSimpleNotification(district: String, coef: Double, isDemo: Boolean) {
        val channelId = "demand_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Оповещения о зонах",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Краткие оповещения без сложных действий"
            }
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        val title = if (isDemo) "Демо: зона ×$coef" else "Рядом зона ×$coef"
        val text = if (isDemo) {
            "$district · демонстрационные данные"
        } else {
            "$district · коэффициент мог измениться"
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        if (ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(applicationContext).notify(1001, notification)
        }
    }

    companion object {
        private const val WORK_NAME = "demand_refresh"

        fun schedule(context: Context, intervalMinutes: Long) {
            val request = PeriodicWorkRequestBuilder<DemandRefreshWorker>(
                intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
