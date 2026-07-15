package com.radar.coefficients.data.context

import com.radar.coefficients.domain.model.GeoPoint
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Погода Open-Meteo (бесплатно, без ключа).
 * Дождь/снег/ветер/жара → выше спрос на такси (открытая эвристика).
 */
@Singleton
class OpenMeteoClient @Inject constructor() {

    data class WeatherSignal(
        val temperatureC: Double,
        val precipitationMm: Double,
        val weatherCode: Int,
        val windKmh: Double,
        /** 0..1 — вклад погоды в кэф */
        val demandBoost: Double,
        val summaryRu: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ForecastResponse::class.java)
    private val cache = ConcurrentHashMap<String, Pair<Long, WeatherSignal>>()

    fun fetch(point: GeoPoint): WeatherSignal {
        val key = "%.2f,%.2f".format(point.latitude, point.longitude)
        val now = System.currentTimeMillis()
        cache[key]?.let { (ts, w) ->
            if (now - ts < 10 * 60_000L) return w
        }
        val signal = runCatching { load(point) }.getOrElse { fallback() }
        cache[key] = now to signal
        return signal
    }

    private fun load(point: GeoPoint): WeatherSignal {
        val url =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${point.latitude}&longitude=${point.longitude}" +
                "&current=temperature_2m,precipitation,weather_code,wind_speed_10m" +
                "&wind_speed_unit=kmh&timezone=auto"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "YaRaDaR/1.1 (open weather context)")
            .get()
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return fallback()
            val body = resp.body?.string() ?: return fallback()
            val parsed = adapter.fromJson(body) ?: return fallback()
            val c = parsed.current ?: return fallback()
            val temp = c.temperature2m ?: 10.0
            val precip = c.precipitation ?: 0.0
            val code = c.weatherCode ?: 0
            val wind = c.windSpeed10m ?: 0.0
            return buildSignal(temp, precip, code, wind)
        }
    }

    private fun buildSignal(
        temp: Double,
        precip: Double,
        code: Int,
        wind: Double
    ): WeatherSignal {
        // WMO weather codes: 51-67 rain, 71-77 snow, 80-82 showers, 95-99 thunder
        val precipBoost = when {
            precip >= 3.0 || code in 80..82 || code in 95..99 -> 0.45
            precip >= 1.0 || code in 61..67 || code in 71..77 -> 0.32
            precip >= 0.2 || code in 51..57 -> 0.18
            code in 45..48 -> 0.12 // fog
            else -> 0.0
        }
        val coldBoost = when {
            temp <= -15 -> 0.28
            temp <= -5 -> 0.18
            temp <= 0 -> 0.1
            temp >= 32 -> 0.12
            else -> 0.0
        }
        val windBoost = when {
            wind >= 55 -> 0.15
            wind >= 40 -> 0.08
            else -> 0.0
        }
        val boost = (precipBoost + coldBoost + windBoost).coerceIn(0.0, 0.7)
        val summary = buildString {
            when {
                precip >= 1.0 || code in 51..99 -> append("осадки")
                code in 45..48 -> append("туман")
                temp <= 0 -> append("мороз")
                temp >= 30 -> append("жара")
                else -> append("ясно/облачно")
            }
            append(" · ${"%.0f".format(temp)}°C")
            if (wind >= 25) append(" · ветер ${"%.0f".format(wind)} км/ч")
            if (boost > 0.05) append(" · +спрос")
        }
        return WeatherSignal(temp, precip, code, wind, boost, summary)
    }

    private fun fallback() = WeatherSignal(
        temperatureC = 10.0,
        precipitationMm = 0.0,
        weatherCode = 0,
        windKmh = 5.0,
        demandBoost = 0.0,
        summaryRu = "погода н/д"
    )

    private data class ForecastResponse(
        val current: CurrentBlock? = null
    )

    private data class CurrentBlock(
        @Json(name = "temperature_2m") val temperature2m: Double? = null,
        val precipitation: Double? = null,
        @Json(name = "weather_code") val weatherCode: Int? = null,
        @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null
    )
}
