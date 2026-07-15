package com.radar.coefficients.data.geocoder

import android.content.Context
import android.location.Geocoder
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Название точки по центру зоны: **улица** (по возможности с номером дома).
 * Android Geocoder → при неудаче OSM Nominatim.
 */
@Singleton
class StreetLabelResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 200
    }
    private val cacheMutex = Mutex()

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val nominatimAdapter = moshi.adapter(NominatimReverse::class.java)

    suspend fun labelAt(point: GeoPoint, fallback: String = "Точка на карте"): String =
        labelAt(point.latitude, point.longitude, fallback)

    suspend fun labelAt(lat: Double, lon: Double, fallback: String = "Точка на карте"): String {
        val key = cacheKey(lat, lon)
        cacheMutex.withLock { cache[key] }?.let { return it }

        val resolved = withContext(Dispatchers.IO) {
            reverseAndroid(lat, lon)
                ?: reverseNominatim(lat, lon)
                ?: fallback
        }.trim().ifBlank { fallback }

        cacheMutex.withLock { cache[key] = resolved }
        return resolved
    }

    /** Подписать зоны улицей в центре полигона / якоря. */
    suspend fun enrichZones(zones: List<DemandZone>): List<DemandZone> = coroutineScope {
        if (zones.isEmpty()) return@coroutineScope zones
        zones.map { zone ->
            async {
                val street = labelAt(
                    zone.center.latitude,
                    zone.center.longitude,
                    fallback = simplifyFallback(zone.districtName)
                )
                if (street == zone.districtName) zone else zone.copy(districtName = street)
            }
        }.awaitAll()
    }

    private fun simplifyFallback(raw: String): String {
        // "Центр · Кострома" → "Центр" (город убираем — улица важнее)
        val left = raw.substringBefore(" · ").trim()
        return left.ifBlank { raw }.ifBlank { "Район" }
    }

    private fun cacheKey(lat: Double, lon: Double): String =
        // ~11 м точность — стабильные подписи при мелком дрейфе
        "%.4f,%.4f".format(Locale.US, lat, lon)

    private fun reverseAndroid(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val geocoder = Geocoder(context, Locale("ru", "RU"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 3).orEmpty()
            list.firstNotNullOfOrNull { formatAddress(it) }
                ?: list.firstNotNullOfOrNull { formatAddressLoose(it) }
        }.getOrNull()
    }

    private fun formatAddress(a: android.location.Address): String? {
        val street = a.thoroughfare?.trim()?.takeIf { it.isNotEmpty() && !isCityOnly(it, a) }
            ?: return null
        val house = a.subThoroughfare?.trim()?.takeIf { it.isNotEmpty() }
        val pretty = prettifyStreet(street)
        return if (house != null) "$pretty, $house" else pretty
    }

    private fun formatAddressLoose(a: android.location.Address): String? {
        val feature = a.featureName?.trim().orEmpty()
        val street = a.thoroughfare?.trim().orEmpty()
        val subLocality = a.subLocality?.trim().orEmpty()
        val locality = a.locality?.trim().orEmpty()

        // featureName часто = номер дома или POI
        when {
            street.isNotEmpty() -> {
                val pretty = prettifyStreet(street)
                val house = a.subThoroughfare?.trim()
                    ?: feature.takeIf { it.matches(Regex("^\\d+[А-Яа-яA-Za-z/\\-]*$")) }
                return if (!house.isNullOrBlank() && house != pretty) "$pretty, $house" else pretty
            }
            feature.isNotEmpty() &&
                feature != locality &&
                !feature.equals(a.adminArea, true) &&
                feature.length in 3..48 -> return feature
            subLocality.isNotEmpty() -> return subLocality
            else -> return null
        }
    }

    private fun isCityOnly(name: String, a: android.location.Address): Boolean {
        val city = a.locality?.trim().orEmpty()
        return city.isNotEmpty() && name.equals(city, ignoreCase = true)
    }

    private fun prettifyStreet(raw: String): String {
        val s = raw.trim()
            .replace(Regex("\\s+"), " ")
        // Уже «улица …» / «ул. …» / «проспект …»
        if (s.contains(Regex("(?i)(улица|ул\\.|проспект|пр-т|пр\\.|переулок|пер\\.|шоссе|бульвар|б-р|площадь|пл\\.|набережная|наб\\.)"))) {
            return s
                .replace(Regex("(?i)^улица\\s+"), "ул. ")
                .replace(Regex("(?i)^проспект\\s+"), "пр-т ")
                .replace(Regex("(?i)^переулок\\s+"), "пер. ")
                .replace(Regex("(?i)^бульвар\\s+"), "б-р ")
                .replace(Regex("(?i)^площадь\\s+"), "пл. ")
                .replace(Regex("(?i)^набережная\\s+"), "наб. ")
                .replace(Regex("(?i)^шоссе\\s+"), "ш. ")
        }
        // Голое имя — по умолчанию улица
        return "ул. $s"
    }

    private fun reverseNominatim(lat: Double, lon: Double): String? {
        return runCatching {
            val url =
                "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon" +
                    "&format=json&zoom=18&addressdetails=1&accept-language=ru"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "YaRaDaR/1.0 (taxi coefficient radar; offline-first)")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                val parsed = nominatimAdapter.fromJson(body) ?: return@use null
                val addr = parsed.address ?: return@use null
                val road = listOfNotNull(
                    addr.road,
                    addr.pedestrian,
                    addr.footway,
                    addr.path,
                    addr.residential
                ).firstOrNull { it.isNotBlank() }
                if (road != null) {
                    val house = addr.houseNumber?.takeIf { it.isNotBlank() }
                    val pretty = prettifyStreet(road)
                    return@use if (house != null) "$pretty, $house" else pretty
                }
                listOfNotNull(
                    addr.neighbourhood,
                    addr.suburb,
                    addr.quarter,
                    addr.cityDistrict
                ).firstOrNull { !it.isNullOrBlank() }?.trim()
            }
        }.getOrNull()
    }

    private data class NominatimReverse(
        val address: NominatimAddress? = null
    )

    private data class NominatimAddress(
        val road: String? = null,
        val pedestrian: String? = null,
        val footway: String? = null,
        val path: String? = null,
        val residential: String? = null,
        @Json(name = "house_number") val houseNumber: String? = null,
        val neighbourhood: String? = null,
        val suburb: String? = null,
        val quarter: String? = null,
        @Json(name = "city_district") val cityDistrict: String? = null
    )
}
