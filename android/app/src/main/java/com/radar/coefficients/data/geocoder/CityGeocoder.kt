package com.radar.coefficients.data.geocoder

import android.content.Context
import android.location.Geocoder
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.VehicleClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverse geocoding via Android Geocoder (device/network provider).
 * Results are cached by callers (Room / in-memory). No scraping.
 */
@Singleton
class CityGeocoder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val memoryCache = LinkedHashMap<String, City>(64, 0.75f, true)

    suspend fun reverseGeocode(latitude: Double, longitude: Double): City? =
        withContext(Dispatchers.IO) {
            val key = "%.2f,%.2f".format(Locale.US, latitude, longitude)
            memoryCache[key]?.let { return@withContext it }

            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = runCatching {
                geocoder.getFromLocation(latitude, longitude, 1)
            }.getOrNull().orEmpty()

            val address = addresses.firstOrNull() ?: return@withContext null
            val cityName = address.locality
                ?: address.subAdminArea
                ?: address.adminArea
                ?: return@withContext null
            val countryCode = address.countryCode ?: "XX"
            val countryName = address.countryName ?: countryCode
            val region = address.adminArea.orEmpty()
            val currency = guessCurrency(countryCode)
            val tz = guessTimeZone(countryCode, region)

            val city = City(
                id = "geo-${countryCode.lowercase()}-${slug(cityName)}",
                name = cityName,
                region = region,
                country = Country(
                    code = countryCode,
                    name = countryName,
                    currencyCode = currency,
                    locale = Locale.getDefault().toLanguageTag(),
                    timeZoneId = tz
                ),
                center = GeoPoint(latitude, longitude),
                bounds = null,
                timeZoneId = tz,
                currencyCode = currency,
                availableVehicleClasses = defaultClasses(),
                availableTariffs = emptyList(),
                demandDataAvailable = false,
                dataAvailability = CityDataAvailability.DEMO_ONLY
            )
            memoryCache[key] = city
            if (memoryCache.size > 80) {
                val first = memoryCache.entries.first().key
                memoryCache.remove(first)
            }
            city
        }

    fun cityFromManualSelection(
        name: String,
        region: String,
        countryCode: String,
        countryName: String,
        latitude: Double,
        longitude: Double,
        currencyCode: String? = null,
        timeZoneId: String? = null
    ): City {
        val currency = currencyCode ?: guessCurrency(countryCode)
        val tz = timeZoneId ?: guessTimeZone(countryCode, region)
        return City(
            id = "manual-${countryCode.lowercase()}-${slug(name)}-${UUID.nameUUIDFromBytes(name.toByteArray()).toString().take(8)}",
            name = name,
            region = region,
            country = Country(
                code = countryCode,
                name = countryName,
                currencyCode = currency,
                locale = Locale.getDefault().toLanguageTag(),
                timeZoneId = tz
            ),
            center = GeoPoint(latitude, longitude),
            bounds = null,
            timeZoneId = tz,
            currencyCode = currency,
            availableVehicleClasses = defaultClasses(),
            availableTariffs = emptyList(),
            demandDataAvailable = false,
            dataAvailability = CityDataAvailability.DEMO_ONLY
        )
    }

    private fun defaultClasses() = listOf(
        VehicleClass.ECONOMY,
        VehicleClass.COMFORT,
        VehicleClass.COMFORT_PLUS,
        VehicleClass.BUSINESS,
        VehicleClass.MINIVAN
    )

    private fun slug(name: String): String =
        name.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9а-яё]+", RegexOption.IGNORE_CASE), "-")
            .trim('-')
            .ifBlank { "city" }

    private fun guessCurrency(countryCode: String): String = when (countryCode.uppercase()) {
        "RU" -> "RUB"
        "KZ" -> "KZT"
        "BY" -> "BYN"
        "UA" -> "UAH"
        "UZ" -> "UZS"
        "AM" -> "AMD"
        "GE" -> "GEL"
        "AZ" -> "AZN"
        "KG" -> "KGS"
        "MD" -> "MDL"
        "US" -> "USD"
        "DE", "FR", "IT", "ES", "NL", "AT", "FI", "EE", "LV", "LT" -> "EUR"
        "TR" -> "TRY"
        "IL" -> "ILS"
        else -> "USD"
    }

    private fun guessTimeZone(countryCode: String, region: String): String = when (countryCode.uppercase()) {
        "RU" -> when {
            region.contains("Моск", true) || region.contains("Moscow", true) -> "Europe/Moscow"
            region.contains("Питер", true) || region.contains("Санкт", true) -> "Europe/Moscow"
            region.contains("Новосиб", true) -> "Asia/Novosibirsk"
            region.contains("Екатер", true) -> "Asia/Yekaterinburg"
            region.contains("Владив", true) -> "Asia/Vladivostok"
            else -> "Europe/Moscow"
        }
        "KZ" -> "Asia/Almaty"
        "BY" -> "Europe/Minsk"
        "UA" -> "Europe/Kyiv"
        "UZ" -> "Asia/Tashkent"
        "GE" -> "Asia/Tbilisi"
        "AM" -> "Asia/Yerevan"
        "TR" -> "Europe/Istanbul"
        "DE" -> "Europe/Berlin"
        "US" -> "America/New_York"
        else -> "UTC"
    }
}
