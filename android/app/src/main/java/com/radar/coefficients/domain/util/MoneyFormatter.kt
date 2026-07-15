package com.radar.coefficients.domain.util

import com.radar.coefficients.domain.model.DisplayCurrency
import com.radar.coefficients.domain.model.MoneyRange
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Форматирование и приблизительная конвертация в выбранную валюту.
 * Курсы ориентировочные — для UI, не для бухгалтерии.
 */
object MoneyFormatter {

    /** Сколько единиц валюты в 1 RUB (приблизительно). */
    private val rubTo: Map<String, Double> = mapOf(
        "RUB" to 1.0,
        "KZT" to 5.2,
        "BYN" to 0.035,
        "UZS" to 140.0,
        "USD" to 0.011,
        "EUR" to 0.010,
        "UAH" to 0.45,
        "GEL" to 0.03,
        "TRY" to 0.38,
        "AMD" to 4.3,
        "AZN" to 0.019,
        "KGS" to 0.95
    )

    fun resolveDisplay(
        preferred: DisplayCurrency,
        cityCurrencyCode: String?
    ): DisplayCurrency =
        if (preferred == DisplayCurrency.AUTO) {
            DisplayCurrency.fromCode(cityCurrencyCode ?: "RUB").let {
                if (it == DisplayCurrency.AUTO) DisplayCurrency.RUB else it
            }
        } else preferred

    fun convert(
        amount: Double,
        fromCurrency: String,
        to: DisplayCurrency,
        cityCurrencyCode: String? = null
    ): Double {
        val target = resolveDisplay(to, cityCurrencyCode)
        if (target.code.equals(fromCurrency, true)) return amount
        val fromRate = rubTo[fromCurrency.uppercase()] ?: 1.0
        val toRate = rubTo[target.code] ?: 1.0
        // amount in from → RUB → target
        val inRub = amount / fromRate
        return inRub * toRate
    }

    fun format(
        amount: Double,
        preferred: DisplayCurrency,
        sourceCurrency: String = "RUB",
        cityCurrencyCode: String? = null,
        withPlus: Boolean = false
    ): String {
        val target = resolveDisplay(preferred, cityCurrencyCode)
        val converted = convert(amount, sourceCurrency, preferred, cityCurrencyCode)
        val rounded = converted.roundToInt()
        val nf = NumberFormat.getIntegerInstance(Locale("ru", "RU"))
        val body = nf.format(rounded)
        val sign = when {
            withPlus && rounded > 0 -> "+"
            rounded < 0 -> ""
            else -> ""
        }
        return "$sign$body ${target.symbol}"
    }

    fun formatRange(
        range: MoneyRange,
        preferred: DisplayCurrency,
        cityCurrencyCode: String? = null
    ): String {
        val a = format(range.min, preferred, range.currencyCode, cityCurrencyCode)
        val b = format(range.max, preferred, range.currencyCode, cityCurrencyCode)
        // avoid double symbols: "1 200 ₽–1 400 ₽"
        return "${a.substringBeforeLast(' ')}–$b"
    }

    fun formatPerHour(
        amountPerHour: Double,
        preferred: DisplayCurrency,
        sourceCurrency: String = "RUB",
        cityCurrencyCode: String? = null
    ): String = "${format(amountPerHour, preferred, sourceCurrency, cityCurrencyCode)}/ч"
}
