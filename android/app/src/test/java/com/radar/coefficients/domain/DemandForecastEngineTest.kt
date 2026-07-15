package com.radar.coefficients.domain

import com.google.common.truth.Truth.assertThat
import com.radar.coefficients.domain.model.ForecastTrend
import com.radar.coefficients.domain.usecase.DemandForecastEngine
import org.junit.Test
import kotlin.random.Random

class DemandForecastEngineTest {

    @Test
    fun `rush hour increases order pressure vs night`() {
        val p = DemandForecastEngine.profiles.first { it.kind == "center" }
        val rnd = Random(1)
        val day = DemandForecastEngine.estimateOrders(p, hour = 18, weekend = false, rnd = rnd)
        val night = DemandForecastEngine.estimateOrders(p, hour = 3, weekend = false, rnd = Random(1))
        assertThat(day.ordersLastHour).isGreaterThan(night.ordersLastHour)
    }

    @Test
    fun `forecast coefficients stay in valid range`() {
        val p = DemandForecastEngine.profiles.first { it.kind == "station" }
        val rnd = Random(42)
        val orders = DemandForecastEngine.estimateOrders(p, 9, false, rnd)
        val coef = DemandForecastEngine.currentCoefficient(p, 9, false, orders, rnd)
        val f = DemandForecastEngine.forecast(coef, p, 9, false, orders, rnd)
        assertThat(coef).isAtLeast(1.0)
        assertThat(coef).isAtMost(2.9)
        assertThat(f.coefficientIn15Min).isAtLeast(1.0)
        assertThat(f.coefficientIn60Min).isAtMost(3.0)
        assertThat(f.igniteProbability30Min).isAtLeast(0.0)
        assertThat(f.igniteProbability30Min).isAtMost(1.0)
    }

    @Test
    fun `heat score higher for hot rising zone`() {
        val p = DemandForecastEngine.profiles.first { it.kind == "airport" }
        val rnd = Random(7)
        val orders = DemandForecastEngine.estimateOrders(p, 19, true, rnd, 1.2)
        val coef = 2.1
        val f = DemandForecastEngine.forecast(coef, p, 19, true, orders, rnd)
            .copy(trend = ForecastTrend.RISING, igniteProbability30Min = 0.8)
        val heat = DemandForecastEngine.heatScore(coef, orders, f)
        assertThat(heat).isGreaterThan(50)
    }
}
