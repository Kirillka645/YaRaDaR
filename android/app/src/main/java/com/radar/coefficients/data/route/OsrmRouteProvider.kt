package com.radar.coefficients.data.route

import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.RouteEstimate
import com.radar.coefficients.domain.model.TollRoadInfo
import com.radar.coefficients.domain.provider.RouteProvider
import com.radar.coefficients.domain.util.GeoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Legal routing via public OSRM demo with short-lived cache and haversine fallback.
 * Does not provide live traffic — UI must state that clearly.
 */
@Singleton
class OsrmRouteProvider @Inject constructor() : RouteProvider {

    override val providerId: String = "osrm"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private data class CacheKey(val a: String, val b: String)
    private val cache = ConcurrentHashMap<CacheKey, Pair<Long, RouteEstimate>>()
    private val cacheTtlMs = 3 * 60_000L

    override suspend fun buildRoute(origin: GeoPoint, destination: GeoPoint): RouteEstimate =
        withContext(Dispatchers.IO) {
            val key = CacheKey(pointKey(origin), pointKey(destination))
            val cached = cache[key]
            if (cached != null && System.currentTimeMillis() - cached.first < cacheTtlMs) {
                return@withContext cached.second
            }

            val osrm = fetchOsrm(origin, destination)
            val result = osrm ?: fallback(origin, destination)
            cache[key] = System.currentTimeMillis() to result
            result
        }

    override suspend fun getDistance(origin: GeoPoint, destination: GeoPoint): Double =
        buildRoute(origin, destination).distanceKm

    override suspend fun getDuration(origin: GeoPoint, destination: GeoPoint): Int =
        buildRoute(origin, destination).durationMinutes

    override suspend fun getDurationWithTraffic(origin: GeoPoint, destination: GeoPoint): Int? =
        null // OSRM public instance has no live traffic

    private fun fetchOsrm(origin: GeoPoint, destination: GeoPoint): RouteEstimate? = runCatching {
        val url =
            "https://router.project-osrm.org/route/v1/driving/" +
                "${origin.longitude},${origin.latitude};" +
                "${destination.longitude},${destination.latitude}" +
                "?overview=false&alternatives=false&steps=false"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "CoefficientRadar/1.0 (legal routing; contact: local-dev)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") return null
            val route = json.getJSONArray("routes").getJSONObject(0)
            val distanceM = route.getDouble("distance")
            val durationS = route.getDouble("duration")
            RouteEstimate(
                origin = origin,
                destination = destination,
                distanceKm = distanceM / 1000.0,
                durationMinutes = (durationS / 60.0).roundToInt().coerceAtLeast(1),
                durationWithTrafficMinutes = null,
                geometry = emptyList(),
                hasTrafficData = false,
                tollInfo = TollRoadInfo(false, null, "—"),
                sourceName = "OSRM",
                calculatedAtEpochMs = System.currentTimeMillis()
            )
        }
    }.getOrNull()

    private fun fallback(origin: GeoPoint, destination: GeoPoint): RouteEstimate {
        val dist = GeoMath.distanceKm(origin, destination) * 1.25 // road factor
        val minutes = GeoMath.estimateTravelMinutes(dist)
        return RouteEstimate(
            origin = origin,
            destination = destination,
            distanceKm = dist,
            durationMinutes = minutes,
            durationWithTrafficMinutes = null,
            geometry = listOf(origin, destination),
            hasTrafficData = false,
            tollInfo = null,
            sourceName = "Haversine fallback",
            calculatedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun pointKey(p: GeoPoint): String =
        "%.3f,%.3f".format(p.latitude, p.longitude)
}
