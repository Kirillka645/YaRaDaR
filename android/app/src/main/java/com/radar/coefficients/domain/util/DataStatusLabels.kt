package com.radar.coefficients.domain.util

import com.radar.coefficients.domain.model.DataStatus
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.CityDataAvailability

object DataStatusLabels {
    fun statusRu(status: DataStatus): String = when (status) {
        DataStatus.REAL -> "Реальные данные"
        DataStatus.PARTNER -> "Партнёрские данные"
        DataStatus.COMMUNITY -> "Сообщено водителями"
        DataStatus.STALE -> "Данные устарели"
        DataStatus.DEMO -> "Демонстрационные данные"
        DataStatus.NONE -> "Нет данных"
    }

    fun sourceTypeRu(type: SourceType): String = when (type) {
        SourceType.OFFICIAL_API -> "Официальный API"
        SourceType.LICENSED_PROVIDER -> "Лицензированный поставщик"
        SourceType.PARTNER_API -> "Партнёрский API"
        SourceType.MANUAL_DRIVER_REPORT -> "Сообщения водителей"
        SourceType.DEMO_PROVIDER -> "Демо-поставщик"
    }

    fun cityAvailabilityRu(a: CityDataAvailability): String = when (a) {
        CityDataAvailability.REAL_DATA -> "Реальные данные доступны"
        CityDataAvailability.COMMUNITY_ONLY -> "Только пользовательские данные"
        CityDataAvailability.DEMO_ONLY -> "Только деморежим"
        CityDataAvailability.NO_DATA -> "Нет данных"
    }

    fun demandLevelRu(level: DemandLevel): String = when (level) {
        DemandLevel.NORMAL -> "Обычный спрос"
        DemandLevel.ELEVATED -> "Повышенный"
        DemandLevel.HIGH -> "Высокий"
        DemandLevel.CRITICAL -> "Очень высокий"
    }

    fun coefficientColorKey(coefficient: Double): String = when {
        coefficient >= 2.0 -> "red"
        coefficient >= 1.5 -> "orange"
        coefficient >= 1.1 -> "yellow"
        else -> "green"
    }

    const val NOT_OFFICIAL =
        "Приложение не является официальным продуктом Яндекса"
    const val DEMO_BANNER =
        "Демонстрационные данные — не использовать для рабочих решений"
    const val GROUNDED_MODEL =
        "Кэф из открытых данных: погода (Open-Meteo) + POI (OpenStreetMap) + час/день. Не Яндекс Про."

    const val REAL_UNAVAILABLE =
        "Реальные коэффициенты для этого города сейчас недоступны"
    const val COEF_MAY_CHANGE =
        "Коэффициент может измениться до прибытия"
}
