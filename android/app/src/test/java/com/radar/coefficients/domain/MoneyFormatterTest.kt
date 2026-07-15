package com.radar.coefficients.domain

import com.google.common.truth.Truth.assertThat
import com.radar.coefficients.domain.model.DisplayCurrency
import com.radar.coefficients.domain.util.MoneyFormatter
import org.junit.Test

class MoneyFormatterTest {
    @Test
    fun `rub format contains ruble symbol`() {
        val s = MoneyFormatter.format(1250.0, DisplayCurrency.RUB)
        assertThat(s).contains("₽")
        assertThat(s).contains("1")
    }

    @Test
    fun `default preferred is rub`() {
        val s = MoneyFormatter.format(100.0, DisplayCurrency.RUB, "RUB")
        assertThat(s).endsWith("₽")
    }

    @Test
    fun `convert same currency unchanged`() {
        val v = MoneyFormatter.convert(500.0, "RUB", DisplayCurrency.RUB)
        assertThat(v).isWithin(0.01).of(500.0)
    }
}
