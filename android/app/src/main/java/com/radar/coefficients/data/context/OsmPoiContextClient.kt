package com.radar.coefficients.data.context

import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.usecase.DemandForecastEngine
import com.radar.coefficients.domain.util.GeoMath
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln

/**
 * Плотность POI OpenStreetMap (Overpass) вокруг точки.
 * Вокзал/ТРЦ/рестораны → выше спрос (открытые данные OSM).
 */
@Singleton
class OsmPoiContextClient @Inject constructor() {

    data class ZonePoiSignal(
        val score: Double,          // 0..1
        val count: Int,
        val kind: String,           // profile kind hint
        val labelRu: String,
        val summaryRu: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(OverpassResponse::class.java)
    private val cityCache = ConcurrentHashMap<String, Pair<Long, List<Poi>>>()

    fun signalsFor(
        cityId: String,
        centers: List<GeoPoint>,
        radiusKm: Double = 0.85
    ): List<ZonePoiSignal> {
        if (centers.isEmpty()) return emptyList()
        val pois = loadCityPois(cityId, centers)
        return centers.map { c -> scoreAround(c, pois, radiusKm) }
    }

    private fun loadCityPois(cityId: String, centers: List<GeoPoint>): List<Poi> {
        val now = System.currentTimeMillis()
        cityCache[cityId]?.let { (ts, list) ->
            if (now - ts < 30 * 60_000L) return list
        }
        val lats = centers.map { it.latitude }
        val lons = centers.map { it.longitude }
        val pad = 0.02
        val south = (lats.minOrNull() ?: 0.0) - pad
        val north = (lats.maxOrNull() ?: 0.0) + pad
        val west = (lons.minOrNull() ?: 0.0) - pad
        val east = (lons.maxOrNull() ?: 0.0) + pad
        val list = runCatching { queryBbox(south, west, north, east) }.getOrDefault(emptyList())
        if (list.isNotEmpty()) cityCache[cityId] = now to list
        return list
    }

    private fun queryBbox(south: Double, west: Double, north: Double, east: Double): List<Poi> {
        // Overpass QL — только спросные объекты
        val q = """
            [out:json][timeout:30];
            (
              nwr["railway"="station"]($south,$west,$north,$east);
              nwr["railway"="halt"]($south,$west,$north,$east);
              nwr["public_transport"="station"]($south,$west,$north,$east);
              nwr["aeroway"="aerodrome"]($south,$west,$north,$east);
              nwr["amenity"="bus_station"]($south,$west,$north,$east);
              nwr["shop"="mall"]($south,$west,$north,$east);
              nwr["amenity"="cinema"]($south,$west,$north,$east);
              nwr["amenity"="theatre"]($south,$west,$north,$east);
              nwr["amenity"="hospital"]($south,$west,$north,$east);
              nwr["amenity"="university"]($south,$west,$north,$east);
              nwr["amenity"="college"]($south,$west,$north,$east);
              nwr["amenity"="marketplace"]($south,$west,$north,$east);
              nwr["leisure"="stadium"]($south,$west,$north,$east);
              nwr["amenity"="restaurant"]($south,$west,$north,$east);
              nwr["amenity"="cafe"]($south,$west,$north,$east);
              nwr["amenity"="bar"]($south,$west,$north,$east);
              nwr["amenity"="pub"]($south,$west,$north,$east);
              nwr["amenity"="fast_food"]($south,$west,$north,$east);
              nwr["amenity"="nightclub"]($south,$west,$north,$east);
            );
            out center tags;
        """.trimIndent()
        val form = "data=${java.net.URLEncoder.encode(q, Charsets.UTF_8.name())}"
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = Request.Builder()
            .url("https://overpass-api.de/api/interpreter")
            .header("User-Agent", "YaRaDaR/1.1 (OSM POI demand context)")
            .post(form)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val json = resp.body?.string() ?: return emptyList()
            val parsed = adapter.fromJson(json) ?: return emptyList()
            return parsed.elements.orEmpty().mapNotNull { el ->
                val lat = el.lat ?: el.center?.lat ?: return@mapNotNull null
                val lon = el.lon ?: el.center?.lon ?: return@mapNotNull null
                val tags = el.tags.orEmpty()
                val w = weightOf(tags)
                if (w <= 0) return@mapNotNull null
                Poi(GeoPoint(lat, lon), w, classify(tags))
            }
        }
    }

    private fun scoreAround(center: GeoPoint, pois: List<Poi>, radiusKm: Double): ZonePoiSignal {
        var sum = 0.0
        var n = 0
        val kinds = mutableMapOf<String, Double>()
        for (p in pois) {
            val d = GeoMath.distanceKm(center, p.point)
            if (d > radiusKm) continue
            // ближе = сильнее
            val fall = 1.0 / (1.0 + d * 2.5)
            val v = p.weight * fall
            sum += v
            n++
            kinds[p.kind] = (kinds[p.kind] ?: 0.0) + v
        }
        // log-scale: 0 POI → 0, много → ~1
        val score = (ln(1.0 + sum) / ln(1.0 + 25.0)).coerceIn(0.0, 1.0)
        val topKind = kinds.maxByOrNull { it.value }?.key ?: "residential"
        val profile = DemandForecastEngine.profiles.firstOrNull { it.kind == topKind }
            ?: DemandForecastEngine.profiles.first { it.kind == "residential" }
        val summary = when {
            n == 0 -> "мало POI OSM"
            score >= 0.65 -> "плотный спрос (OSM)"
            score >= 0.35 -> "средний спрос (OSM)"
            else -> "тихий район (OSM)"
        } + " · $n точек"
        return ZonePoiSignal(score, n, profile.kind, profile.labelRu, summary)
    }

    private fun weightOf(tags: Map<String, String>): Double {
        val amenity = tags["amenity"]
        val railway = tags["railway"]
        val shop = tags["shop"]
        val aeroway = tags["aeroway"]
        val leisure = tags["leisure"]
        val pt = tags["public_transport"]
        return when {
            aeroway == "aerodrome" -> 4.0
            railway == "station" || pt == "station" -> 3.5
            railway == "halt" -> 2.0
            amenity == "bus_station" -> 2.5
            shop == "mall" -> 3.0
            leisure == "stadium" -> 3.2
            amenity == "hospital" -> 2.0
            amenity == "university" || amenity == "college" -> 2.2
            amenity == "cinema" || amenity == "theatre" -> 1.8
            amenity == "marketplace" -> 2.0
            amenity == "nightclub" -> 1.6
            amenity == "restaurant" || amenity == "bar" || amenity == "pub" -> 0.7
            amenity == "cafe" || amenity == "fast_food" -> 0.5
            else -> 0.0
        }
    }

    private fun classify(tags: Map<String, String>): String = when {
        tags["aeroway"] == "aerodrome" -> "airport"
        tags["railway"] == "station" || tags["public_transport"] == "station" ||
            tags["amenity"] == "bus_station" -> "station"
        tags["shop"] == "mall" -> "mall"
        tags["leisure"] == "stadium" -> "stadium"
        tags["amenity"] == "hospital" -> "hospital"
        tags["amenity"] == "university" || tags["amenity"] == "college" -> "university"
        tags["amenity"] == "marketplace" -> "market"
        tags["amenity"] in setOf("restaurant", "cafe", "bar", "pub", "nightclub", "cinema", "theatre") ->
            "center"
        else -> "residential"
    }

    private data class Poi(val point: GeoPoint, val weight: Double, val kind: String)

    private data class OverpassResponse(
        val elements: List<Element>? = null
    )

    private data class Element(
        val lat: Double? = null,
        val lon: Double? = null,
        val center: Center? = null,
        val tags: Map<String, String>? = null
    )

    private data class Center(
        val lat: Double? = null,
        val lon: Double? = null
    )
}
