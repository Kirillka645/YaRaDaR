package com.radar.coefficients.domain.model

data class UserSettings(
    val minCoefficientAlert: Double = 1.5,
    val notificationRadiusKm: Int = 5,
    val refreshIntervalMinutes: Int = 3,
    val mapRadiusKm: Int = 10,
    val showTraffic: Boolean = true,
    val demoModeEnabled: Boolean = false,
    val selectedVehicleClass: VehicleClass = VehicleClass.ECONOMY,
    val fuelCostPerKm: Double = 6.0,
    val timeCostPerMinute: Double = 5.0,
    val notificationsEnabled: Boolean = true,
    val selectedCityId: String? = null,
    val lastKnownLatitude: Double? = null,
    val lastKnownLongitude: Double? = null,
    val favoriteCityIds: Set<String> = emptySet(),
    val recentCityIds: List<String> = emptyList(),
    /**
     * Какие тарифы показывать над машинкой на карте.
     * По умолчанию: Эконом, Комфорт, Детский.
     */
    val mapVisibleTariffs: Set<VehicleClass> = DEFAULT_MAP_TARIFFS
) {
    companion object {
        val DEFAULT_MAP_TARIFFS: Set<VehicleClass> = setOf(
            VehicleClass.ECONOMY,
            VehicleClass.COMFORT,
            VehicleClass.CHILD
        )
    }
}

enum class RadarSortMode {
    MAX_BENEFIT,
    HIGHEST_COEFFICIENT,
    NEAREST
}

enum class MapRadiusFilter(val km: Int, val labelRu: String) {
    R3(3, "3 км"),
    R5(5, "5 км"),
    R10(10, "10 км"),
    R20(20, "20 км");

    companion object {
        fun fromKm(km: Int): MapRadiusFilter =
            entries.firstOrNull { it.km == km } ?: R10
    }
}
