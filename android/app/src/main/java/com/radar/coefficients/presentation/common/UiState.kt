package com.radar.coefficients.presentation.common

sealed class UiMessage {
    data object Loading : UiMessage()
    data object EmptyZones : UiMessage()
    data object NoInternet : UiMessage()
    data object NoLocation : UiMessage()
    data object StaleData : UiMessage()
    data class Error(val message: String) : UiMessage()
    data object RealDataUnavailable : UiMessage()
}

fun UiMessage.titleRu(): String = when (this) {
    UiMessage.Loading -> "Загрузка"
    UiMessage.EmptyZones -> "Нет доступных зон"
    UiMessage.NoInternet -> "Нет подключения к интернету"
    UiMessage.NoLocation -> "Нет доступа к геолокации"
    UiMessage.StaleData -> "Данные устарели"
    is UiMessage.Error -> "Ошибка получения данных"
    UiMessage.RealDataUnavailable -> "Реальные коэффициенты недоступны"
}

fun UiMessage.bodyRu(): String = when (this) {
    UiMessage.Loading -> "Обновляем зоны спроса…"
    UiMessage.EmptyZones -> "В выбранном радиусе зоны не найдены. Увеличьте радиус или смените город."
    UiMessage.NoInternet -> "Проверьте сеть. Можно открыть кэш или деморежим."
    UiMessage.NoLocation -> "Разрешите геолокацию или выберите город вручную."
    UiMessage.StaleData -> "Данные могли измениться. Обновите вручную."
    is UiMessage.Error -> message
    UiMessage.RealDataUnavailable ->
        "Реальные коэффициенты для этого города сейчас недоступны. " +
            "Можно включить деморежим или смотреть сообщения водителей."
}
