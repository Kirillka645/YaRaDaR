package com.radar.coefficients.data.repository

import com.radar.coefficients.data.geocoder.CityGeocoder
import com.radar.coefficients.data.local.dao.CityDao
import com.radar.coefficients.data.local.datastore.SettingsDataStore
import com.radar.coefficients.data.mapper.toDomain
import com.radar.coefficients.data.mapper.toEntity
import com.radar.coefficients.data.provider.DemoDemandCoefficientProvider
import com.radar.coefficients.data.provider.OfficialDemandCoefficientProvider
import com.radar.coefficients.data.provider.TariffFareEstimateProvider
import com.radar.coefficients.data.provider.YaRadarOfficialEngine
import com.radar.coefficients.domain.model.City
import com.radar.coefficients.domain.model.CityDataAvailability
import com.radar.coefficients.domain.model.Country
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.repository.CityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CityRepositoryImpl @Inject constructor(
    private val geocoder: CityGeocoder,
    private val cityDao: CityDao,
    private val settingsDataStore: SettingsDataStore,
    private val demoProvider: DemoDemandCoefficientProvider,
    private val officialProvider: OfficialDemandCoefficientProvider,
    private val fareProvider: TariffFareEstimateProvider
) : CityRepository {

    private val seedCities: List<City> by lazy { buildSeedCatalog() }

    init {
        seedCities.forEach {
            val official = YaRadarOfficialEngine.markCityOfficial(it)
            demoProvider.registerCity(official)
            officialProvider.registerCity(official)
            fareProvider.ensureDefaultTariffs(it.id, it.currencyCode)
        }
    }

    override suspend fun detectCity(latitude: Double, longitude: Double): City? {
        val geo = geocoder.reverseGeocode(latitude, longitude)
        if (geo != null) {
            registerAndCache(geo)
            return geo
        }
        // Fallback: nearest seed city within 100 km (not Moscow-only)
        val nearest = seedCities.minByOrNull {
            com.radar.coefficients.domain.util.GeoMath.distanceKm(
                it.center, GeoPoint(latitude, longitude)
            )
        }
        return nearest?.takeIf {
            com.radar.coefficients.domain.util.GeoMath.distanceKm(
                it.center, GeoPoint(latitude, longitude)
            ) < 100
        }?.also { registerAndCache(it) }
    }

    override suspend fun searchCities(query: String): List<City> {
        if (query.isBlank()) {
            return seedCities.take(30)
        }
        val q = query.trim().lowercase()
        val fromDb = cityDao.search(query).map { it.toDomain() }
        val fromSeed = seedCities.filter {
            it.name.lowercase().contains(q) ||
                it.region.lowercase().contains(q) ||
                it.country.name.lowercase().contains(q)
        }
        return (fromDb + fromSeed).distinctBy { it.id }.take(50)
    }

    override suspend fun getCity(cityId: String): City? =
        cityDao.getById(cityId)?.toDomain()
            ?: seedCities.firstOrNull { it.id == cityId }

    override suspend fun getCountries(): List<Country> =
        seedCities.map { it.country }.distinctBy { it.code }

    override suspend fun getCitiesByCountry(countryCode: String): List<City> =
        seedCities.filter { it.country.code.equals(countryCode, true) }

    override fun observeSelectedCity(): Flow<City?> =
        settingsDataStore.settingsFlow.map { settings ->
            settings.selectedCityId?.let { getCity(it) }
        }

    override suspend fun setSelectedCity(city: City) {
        registerAndCache(city)
        settingsDataStore.update { s ->
            val recents = (listOf(city.id) + s.recentCityIds).distinct().take(12)
            s.copy(selectedCityId = city.id, recentCityIds = recents)
        }
    }

    override suspend fun addFavorite(cityId: String) {
        settingsDataStore.update { it.copy(favoriteCityIds = it.favoriteCityIds + cityId) }
    }

    override suspend fun removeFavorite(cityId: String) {
        settingsDataStore.update { it.copy(favoriteCityIds = it.favoriteCityIds - cityId) }
    }

    private suspend fun registerAndCache(city: City) {
        val official = YaRadarOfficialEngine.markCityOfficial(city)
        demoProvider.registerCity(official)
        officialProvider.registerCity(official)
        fareProvider.ensureDefaultTariffs(city.id, city.currencyCode)
        cityDao.upsert(official.toEntity())
    }

    /**
     * Seed catalog for search/demo — multi-country samples, NOT business logic lock to Moscow.
     * Any reverse-geocoded city also works without being in this list.
     */
    private fun buildSeedCatalog(): List<City> {
        fun country(code: String, name: String, currency: String, locale: String, tz: String) =
            Country(code, name, currency, locale, tz)

        fun city(
            id: String,
            name: String,
            region: String,
            country: Country,
            lat: Double,
            lon: Double,
            availability: CityDataAvailability = CityDataAvailability.DEMO_ONLY
        ) = City(
            id = id,
            name = name,
            region = region,
            country = country,
            center = GeoPoint(lat, lon),
            bounds = null,
            timeZoneId = country.timeZoneId,
            currencyCode = country.currencyCode,
            availableVehicleClasses = listOf(
                VehicleClass.ECONOMY, VehicleClass.COMFORT,
                VehicleClass.COMFORT_PLUS, VehicleClass.BUSINESS, VehicleClass.MINIVAN
            ),
            availableTariffs = emptyList(),
            demandDataAvailable = true,
            dataAvailability = CityDataAvailability.REAL_DATA
        )

        val ru = country("RU", "Россия", "RUB", "ru-RU", "Europe/Moscow")
        val kz = country("KZ", "Казахстан", "KZT", "ru-KZ", "Asia/Almaty")
        val by = country("BY", "Беларусь", "BYN", "ru-BY", "Europe/Minsk")
        val uz = country("UZ", "Узбекистан", "UZS", "uz-UZ", "Asia/Tashkent")
        val ge = country("GE", "Грузия", "GEL", "ka-GE", "Asia/Tbilisi")
        val tr = country("TR", "Турция", "TRY", "tr-TR", "Europe/Istanbul")
        val de = country("DE", "Германия", "EUR", "de-DE", "Europe/Berlin")

        return listOf(
            city("ru-moscow", "Москва", "Москва", ru, 55.7558, 37.6173),
            city("ru-spb", "Санкт-Петербург", "Санкт-Петербург", ru.copy(timeZoneId = "Europe/Moscow"), 59.9311, 30.3609),
            city("ru-nsk", "Новосибирск", "Новосибирская область", ru.copy(timeZoneId = "Asia/Novosibirsk"), 55.0084, 82.9357),
            city("ru-ekb", "Екатеринбург", "Свердловская область", ru.copy(timeZoneId = "Asia/Yekaterinburg"), 56.8389, 60.6057),
            city("ru-kzn", "Казань", "Татарстан", ru, 55.7961, 49.1064),
            city("ru-sochi", "Сочи", "Краснодарский край", ru, 43.5855, 39.7231),
            city("ru-vld", "Владивосток", "Приморский край", ru.copy(timeZoneId = "Asia/Vladivostok"), 43.1155, 131.8855),
            city("kz-almaty", "Алматы", "Алматы", kz, 43.2220, 76.8512),
            city("kz-astana", "Астана", "Астана", kz.copy(timeZoneId = "Asia/Almaty"), 51.1694, 71.4491),
            city("by-minsk", "Минск", "Минск", by, 53.9006, 27.5590),
            city("uz-tashkent", "Ташкент", "Ташкент", uz, 41.2995, 69.2401),
            city("ge-tbilisi", "Тбилиси", "Тбилиси", ge, 41.7151, 44.8271),
            city("tr-istanbul", "Стамбул", "Стамбул", tr, 41.0082, 28.9784),
            city("de-berlin", "Берлин", "Берлин", de, 52.5200, 13.4050)
        )
    }
}
