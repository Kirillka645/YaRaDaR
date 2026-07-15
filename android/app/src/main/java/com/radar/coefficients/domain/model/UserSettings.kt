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
    val mapVisibleTariffs: Set<VehicleClass> = DEFAULT_MAP_TARIFFS,
    /** Валюта отображения сумм (по умолчанию рубли). */
    val displayCurrency: DisplayCurrency = DisplayCurrency.RUB,
    /** Не гасить экран на карте (удобно за рулём). */
    val keepScreenOn: Boolean = true,
    /** На карте только «горячие» зоны (кэф ≥ мин. для уведомлений). */
    val showOnlyHotZones: Boolean = false,
    /** Автообновление зон на карте. */
    val autoRefreshEnabled: Boolean = true,
    /** Показывать ориентир. выгоду в ₽ на карточке зоны. */
    val showMoneyOnMap: Boolean = true,
    /** Компактная подпись над машинкой (в одну строку). */
    val compactDriverBubble: Boolean = false,
    /** Счётчик смены: старт epoch ms, 0 = не начата. */
    val shiftStartedAtEpochMs: Long = 0L,
    val shiftZonesChecked: Int = 0,
    /** Показывать прогноз кэфа и заказы по району */
    val showForecastAndOrders: Boolean = true,
    /** Подсвечивать зоны, где кэф «загорится» */
    val highlightPredictedIgnite: Boolean = true
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
    NEAREST,
    /** Где кэф «загорится» / растёт */
    FORECAST_IGNITE,
    /** Больше заказов в районе */
    MOST_ORDERS
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
