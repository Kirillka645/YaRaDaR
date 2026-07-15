package com.radar.coefficients.domain.model

/**
 * Валюта отображения сумм в UI.
 * По умолчанию — рубли. Конвертация приблизительная (не курс ЦБ).
 */
enum class DisplayCurrency(
    val code: String,
    val symbol: String,
    val titleRu: String
) {
    RUB("RUB", "₽", "Рубли"),
    KZT("KZT", "₸", "Тенге"),
    BYN("BYN", "Br", "Белорусский рубль"),
    UZS("UZS", "сўм", "Сум"),
    USD("USD", "$", "Доллары"),
    EUR("EUR", "€", "Евро"),
    AUTO("AUTO", "·", "Как в городе");

    companion object {
        fun fromCode(code: String?): DisplayCurrency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: RUB
    }
}
