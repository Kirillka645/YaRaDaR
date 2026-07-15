package com.radar.coefficients

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.radar.coefficients.data.worker.DemandRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration as OsmConfiguration
import javax.inject.Inject

@HiltAndroidApp
class RadarApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        OsmConfiguration.getInstance().userAgentValue = packageName
        OsmConfiguration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        DemandRefreshWorker.schedule(this, 15)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
