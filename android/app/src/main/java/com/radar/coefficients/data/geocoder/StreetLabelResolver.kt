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
import kotlinx.coroutines.delay
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
 * Улица в координатах центра зоны.
 * Geocoder (устройство) → при неудаче Nominatim (последовательно, без 429).
 */
@Singleton
class StreetLabelResolver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cache = object : LinkedHashMap<String, String>(160, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > 220
    }
    private val cacheMutex = Mutex()
    private val netMutex = Mutex() // Nominatim — не параллелить

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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
                ?: simplifyFallback(fallback)
        }.let { cleanLabel(it) }.ifBlank { simplifyFallback(fallback) }

        cacheMutex.withLock { cache[key] = resolved }
        return resolved
    }

    /** По очереди — стабильные улицы, без гонки и rate-limit. */
    suspend fun enrichZones(zones: List<DemandZone>): List<DemandZone> {
        if (zones.isEmpty()) return zones
        return zones.mapIndexed { idx, zone ->
            if (idx > 0) delay(40)
            val street = labelAt(
                zone.center.latitude,
                zone.center.longitude,
                fallback = simplifyFallback(zone.districtName)
            )
            if (street == zone.districtName) zone else zone.copy(districtName = street)
        }
    }

    private fun simplifyFallback(raw: String): String {
        val left = raw.substringBefore(" · ").trim()
        return left.ifBlank { raw }.ifBlank { "Район" }
    }

    private fun cacheKey(lat: Double, lon: Double): String =
        "%.4f,%.4f".format(Locale.US, lat, lon)

    private fun reverseAndroid(lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val geocoder = Geocoder(context, Locale("ru", "RU"))
            @Suppress("DEPRECATION")
            val list = geocoder.getFromLocation(lat, lon, 5).orEmpty()
            list.asSequence()
                .mapNotNull { formatAddress(it) }
                .firstOrNull()
        }.getOrNull()
    }

    private fun formatAddress(a: android.location.Address): String? {
        val locality = a.locality?.trim().orEmpty()
        val admin = a.adminArea?.trim().orEmpty()
        val thoroughfare = a.thoroughfare?.trim().orEmpty()
        val sub = a.subThoroughfare?.trim().orEmpty()
        val feature = a.featureName?.trim().orEmpty()
        val subLocality = a.subLocality?.trim().orEmpty()

        // 1) нормальная улица
        if (thoroughfare.isNotEmpty() &&
            !thoroughfare.equals(locality, true) &&
            !thoroughfare.equals(admin, true) &&
            !looksLikeCoords(thoroughfare)
        ) {
            val pretty = prettifyStreet(thoroughfare)
            val house = when {
                sub.isNotEmpty() -> sub
                feature.matches(HOUSE_RE) && feature != thoroughfare -> feature
                else -> null
            }
            return if (house != null) "$pretty, $house" else pretty
        }

        // 2) feature как «ул. X» целиком
        if (feature.isNotEmpty() &&
            feature != locality &&
            !feature.equals(admin, true) &&
            !looksLikeCoords(feature) &&
            feature.length in 4..56 &&
            !feature.matches(HOUSE_RE)
        ) {
            // часто "12" уже отфильтровали; "микрорайон X" ок
            return if (STREET_HINT.containsMatchIn(feature)) {
                prettifyStreet(feature)
            } else if (subLocality.isNotEmpty()) {
                subLocality
            } else {
                feature
            }
        }

        if (subLocality.isNotEmpty() && subLocality != locality) return subLocality
        return null
    }

    private fun prettifyStreet(raw: String): String {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (STREET_HINT.containsMatchIn(s)) {
            return s
                .replace(Regex("(?i)^улица\\s+"), "ул. ")
                .replace(Regex("(?i)^проспект\\s+"), "пр-т ")
                .replace(Regex("(?i)^переулок\\s+"), "пер. ")
                .replace(Regex("(?i)^бульвар\\s+"), "б-р ")
                .replace(Regex("(?i)^площадь\\s+"), "пл. ")
                .replace(Regex("(?i)^набережная\\s+"), "наб. ")
                .replace(Regex("(?i)^шоссе\\s+"), "ш. ")
                .replace(Regex("(?i)^проезд\\s+"), "пр. ")
        }
        // не добавляем «ул.» к «мкр …», «СНТ …»
        if (s.contains(Regex("(?i)(мкр|микрорайон|снт|пос[её]лок|квартал|жк\\b)"))) {
            return s
        }
        return "ул. $s"
    }

    private fun cleanLabel(s: String): String =
        s.trim()
            .replace(Regex("\\s+,"), ",")
            .replace(Regex(",\\s*"), ", ")
            .take(64)

    private fun looksLikeCoords(s: String): Boolean =
        s.matches(Regex("""^-?\d+[.,]\d+.*"""))

    private suspend fun reverseNominatim(lat: Double, lon: Double): String? =
        netMutex.withLock {
            runCatching {
                delay(200) // ~1 req / 200ms — мягче к Nominatim
                val url =
                    "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon" +
                        "&format=json&zoom=17&addressdetails=1&accept-language=ru"
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "YaRaDaR/1.0.8 (https://github.com/Kirillka645/YaRaDaR)")
                    .get()
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val parsed = nominatimAdapter.fromJson(body) ?: return@use null
                    val addr = parsed.address ?: return@use null
                    val road = listOfNotNull(
                        addr.road, addr.pedestrian, addr.residential, addr.footway
                    ).firstOrNull { !it.isNullOrBlank() }
                    if (road != null) {
                        val pretty = prettifyStreet(road)
                        val house = addr.houseNumber?.takeIf { it.isNotBlank() }
                        return@use if (house != null) "$pretty, $house" else pretty
                    }
                    listOfNotNull(
                        addr.neighbourhood, addr.suburb, addr.quarter, addr.cityDistrict
                    ).firstOrNull { !it.isNullOrBlank() }?.trim()
                }
            }.getOrNull()
        }

    private data class NominatimReverse(val address: NominatimAddress? = null)

    private data class NominatimAddress(
        val road: String? = null,
        val pedestrian: String? = null,
        val footway: String? = null,
        val residential: String? = null,
        @Json(name = "house_number") val houseNumber: String? = null,
        val neighbourhood: String? = null,
        val suburb: String? = null,
        val quarter: String? = null,
        @Json(name = "city_district") val cityDistrict: String? = null
    )

    companion object {
        private val HOUSE_RE = Regex("""^\d+[А-Яа-яA-Za-z/\-]*$""")
        private val STREET_HINT = Regex(
            "(?i)(улица|ул\\.|проспект|пр-т|пр\\.|переулок|пер\\.|шоссе|бульвар|б-р|площадь|пл\\.|набережная|наб\\.|проезд)"
        )
    }
}
