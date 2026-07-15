package com.radar.coefficients.domain.model

/**
 * Geographic and city configuration models.
 * App is not tied to any single city — city is detected or chosen by the user.
 */

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class GeoBounds(
    val southWest: GeoPoint,
    val northEast: GeoPoint
) {
    fun contains(point: GeoPoint): Boolean =
        point.latitude in southWest.latitude..northEast.latitude &&
            point.longitude in southWest.longitude..northEast.longitude
}

data class Country(
    val code: String,
    val name: String,
    val currencyCode: String,
    val locale: String,
    val timeZoneId: String
)

data class City(
    val id: String,
    val name: String,
    val region: String,
    val country: Country,
    val center: GeoPoint,
    val bounds: GeoBounds?,
    val timeZoneId: String,
    val currencyCode: String,
    val availableVehicleClasses: List<VehicleClass>,
    val availableTariffs: List<TaxiTariff>,
    val demandDataAvailable: Boolean,
    val dataAvailability: CityDataAvailability = CityDataAvailability.DEMO_ONLY
)

enum class CityDataAvailability {
    REAL_DATA,
    COMMUNITY_ONLY,
    DEMO_ONLY,
    NO_DATA
}

enum class VehicleClass(
    val displayNameRu: String,
    /** Короткая метка на карте: Э, К, Д… */
    val shortLabel: String
) {
    ECONOMY("Эконом", "Э"),
    COMFORT("Комфорт", "К"),
    COMFORT_PLUS("Комфорт+", "К+"),
    BUSINESS("Бизнес", "Б"),
    MINIVAN("Минивэн", "М"),
    CHILD("Детский", "Д"),
    COURIER("Курьер", "Ку"),
    OTHER("Другой", "?");

    companion object {
        /** Все тарифы, доступные для настройки на карте */
        val configurable: List<VehicleClass> =
            entries.filter { it != OTHER }
    }
}
