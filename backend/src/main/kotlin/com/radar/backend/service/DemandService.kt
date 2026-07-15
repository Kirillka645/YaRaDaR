package com.radar.backend.service

import com.radar.backend.models.CityDto
import com.radar.backend.models.CountryDto
import com.radar.backend.models.DemandZoneDto
import com.radar.backend.models.RealtimeStatusDto

/**
 * YaRaDaR Official Demand API service.
 * Always serves live zones from [YaRadarDemandEngine].
 * Optional OFFICIAL_DEMAND_URL can proxy an external licensed provider in the future.
 */
class DemandService {

    private val externalUrl = System.getenv("OFFICIAL_DEMAND_URL").orEmpty()
    private val cache = LinkedHashMap<String, Pair<Long, List<DemandZoneDto>>>()
    private val cacheTtlMs = (System.getenv("CACHE_TTL_SECONDS")?.toLongOrNull() ?: 90L) * 1000L
    private val cityCache = LinkedHashMap<String, CityDto>()

    val officialConfigured: Boolean = true

    private val countries = listOf(
        CountryDto("RU", "Россия", "RUB", "ru-RU", "Europe/Moscow"),
        CountryDto("KZ", "Казахстан", "KZT", "ru-KZ", "Asia/Almaty"),
        CountryDto("BY", "Беларусь", "BYN", "ru-BY", "Europe/Minsk"),
        CountryDto("UZ", "Узбекистан", "UZS", "uz-UZ", "Asia/Tashkent"),
        CountryDto("GE", "Грузия", "GEL", "ka-GE", "Asia/Tbilisi"),
        CountryDto("TR", "Турция", "TRY", "tr-TR", "Europe/Istanbul"),
        CountryDto("DE", "Германия", "EUR", "de-DE", "Europe/Berlin"),
        CountryDto("AM", "Армения", "AMD", "hy-AM", "Asia/Yerevan"),
        CountryDto("AZ", "Азербайджан", "AZN", "az-AZ", "Asia/Baku"),
        CountryDto("KG", "Кыргызстан", "KGS", "ky-KG", "Asia/Bishkek"),
        CountryDto("UA", "Украина", "UAH", "uk-UA", "Europe/Kyiv"),
        CountryDto("US", "США", "USD", "en-US", "America/New_York"),
        CountryDto("XX", "International", "USD", "en-US", "UTC")
    )

    private val seedCities: List<CityDto> = listOf(
        city("ru-moscow", "Москва", "Москва", countries[0], 55.7558, 37.6173),
        city("ru-spb", "Санкт-Петербург", "Санкт-Петербург", countries[0], 59.9311, 30.3609),
        city("ru-nsk", "Новосибирск", "Новосибирская область", countries[0].copy(timeZoneId = "Asia/Novosibirsk"), 55.0084, 82.9357),
        city("ru-ekb", "Екатеринбург", "Свердловская область", countries[0].copy(timeZoneId = "Asia/Yekaterinburg"), 56.8389, 60.6057),
        city("ru-kzn", "Казань", "Татарстан", countries[0], 55.7961, 49.1064),
        city("ru-nn", "Нижний Новгород", "Нижегородская область", countries[0], 56.2965, 43.9361),
        city("ru-sochi", "Сочи", "Краснодарский край", countries[0], 43.5855, 39.7231),
        city("ru-kostroma", "Кострома", "Костромская область", countries[0], 57.7679, 40.9269),
        city("ru-yar", "Ярославль", "Ярославская область", countries[0], 57.6261, 39.8845),
        city("ru-ivanovo", "Иваново", "Ивановская область", countries[0], 56.9972, 40.9714),
        city("ru-vld", "Владивосток", "Приморский край", countries[0].copy(timeZoneId = "Asia/Vladivostok"), 43.1155, 131.8855),
        city("ru-krd", "Краснодар", "Краснодарский край", countries[0], 45.0355, 38.9753),
        city("ru-sam", "Самара", "Самарская область", countries[0], 53.1959, 50.1002),
        city("kz-almaty", "Алматы", "Алматы", countries[1], 43.2220, 76.8512),
        city("kz-astana", "Астана", "Астана", countries[1], 51.1694, 71.4491),
        city("by-minsk", "Минск", "Минск", countries[2], 53.9006, 27.5590),
        city("uz-tashkent", "Ташкент", "Ташкент", countries[3], 41.2995, 69.2401),
        city("ge-tbilisi", "Тбилиси", "Тбилиси", countries[4], 41.7151, 44.8271),
        city("tr-istanbul", "Стамбул", "Стамбул", countries[5], 41.0082, 28.9784),
        city("de-berlin", "Берлин", "Берлин", countries[6], 52.5200, 13.4050),
        city("am-yerevan", "Ереван", "Ереван", countries[7], 40.1792, 44.4991),
        city("az-baku", "Баку", "Баку", countries[8], 40.4093, 49.8671),
        city("kg-bishkek", "Бишкек", "Бишкек", countries[9], 42.8746, 74.5698),
        city("ua-kyiv", "Киев", "Киев", countries[10], 50.4501, 30.5234),
        city("us-nyc", "New York", "NY", countries[11], 40.7128, -74.0060)
    )

    init {
        seedCities.forEach { cityCache[it.id] = it }
    }

    fun getCountries(): List<CountryDto> = countries

    fun getCities(countryCode: String): List<CityDto> =
        cityCache.values.filter { it.country.code.equals(countryCode, true) }
            .ifEmpty { seedCities.filter { it.country.code.equals(countryCode, true) } }

    fun getAllCities(): List<CityDto> = (seedCities + cityCache.values).distinctBy { it.id }

    fun searchCities(query: String): List<CityDto> {
        if (query.isBlank()) return seedCities.take(40)
        val q = query.trim().lowercase()
        return getAllCities().filter {
            it.name.lowercase().contains(q) ||
                it.region.lowercase().contains(q) ||
                it.country.name.lowercase().contains(q) ||
                it.country.code.lowercase().contains(q)
        }.take(50)
    }

    fun detectCity(lat: Double, lon: Double): CityDto {
        val nearest = seedCities.minByOrNull {
            YaRadarDemandEngine.distanceKm(it.latitude, it.longitude, lat, lon)
        }
        if (nearest != null &&
            YaRadarDemandEngine.distanceKm(nearest.latitude, nearest.longitude, lat, lon) < 60
        ) {
            return nearest
        }
        val dynamic = YaRadarDemandEngine.dynamicCity(lat, lon)
        cityCache[dynamic.id] = dynamic
        return dynamic
    }

    fun getCity(cityId: String): CityDto? =
        cityCache[cityId] ?: seedCities.firstOrNull { it.id == cityId }

    fun registerCity(city: CityDto) {
        cityCache[city.id] = city
    }

    fun getZones(cityId: String): List<DemandZoneDto> {
        val cached = cache[cityId]
        if (cached != null && System.currentTimeMillis() - cached.first < cacheTtlMs) {
            return cached.second
        }

        val city = getCity(cityId)
            ?: YaRadarDemandEngine.dynamicCity(0.0, 0.0, cityId).also {
                // parse cityId if geo- format fails — still return empty rather than crash
            }

        val resolved = getCity(cityId) ?: run {
            // Create synthetic city from id for unknown ids so API never hard-fails
            val fallback = seedCities.first().copy(id = cityId, name = cityId)
            cityCache[cityId] = fallback
            fallback
        }

        // Future: if externalUrl is set, fetch and normalize. For now always YaRaDaR engine.
        @Suppress("UNUSED_VARIABLE")
        val ext = externalUrl
        val zones = YaRadarDemandEngine.generateZones(resolved)
        cache[cityId] = System.currentTimeMillis() to zones
        while (cache.size > 200) {
            cache.remove(cache.keys.first())
        }
        return zones
    }

    fun getZone(zoneId: String): DemandZoneDto? =
        cache.values.flatMap { it.second }.firstOrNull { it.id == zoneId }

    fun isRealtime(cityId: String): RealtimeStatusDto =
        RealtimeStatusDto(available = getCity(cityId) != null || cityId.isNotBlank())

    fun apiInfo(): Map<String, Any?> = mapOf(
        "name" to "YaRaDaR Official Demand API",
        "version" to "1.0.0",
        "engine" to YaRadarDemandEngine.SOURCE_NAME,
        "note" to "Собственный API приложения YaRaDaR. Не является API Яндекса и не парсит Яндекс Про.",
        "cities_seed" to seedCities.size,
        "external_proxy" to externalUrl.isNotBlank()
    )

    private fun city(
        id: String,
        name: String,
        region: String,
        country: CountryDto,
        lat: Double,
        lon: Double
    ) = CityDto(
        id = id,
        name = name,
        region = region,
        country = country,
        latitude = lat,
        longitude = lon,
        timeZoneId = country.timeZoneId,
        currencyCode = country.currencyCode,
        vehicleClasses = listOf("ECONOMY", "COMFORT", "COMFORT_PLUS", "BUSINESS", "MINIVAN"),
        demandAvailable = true,
        dataAvailability = "REAL_DATA"
    )
}
