package com.radar.coefficients.domain

import com.google.common.truth.Truth.assertThat
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.util.GeoMath
import org.junit.Test

class GeoMathTest {
    @Test
    fun `distance between same points is zero`() {
        val p = GeoPoint(10.0, 20.0)
        assertThat(GeoMath.distanceKm(p, p)).isWithin(0.0001).of(0.0)
    }

    @Test
    fun `distance moscow spb roughly 600 plus km`() {
        val moscow = GeoPoint(55.7558, 37.6173)
        val spb = GeoPoint(59.9311, 30.3609)
        val d = GeoMath.distanceKm(moscow, spb)
        assertThat(d).isGreaterThan(600.0)
        assertThat(d).isLessThan(750.0)
    }

    @Test
    fun `polygon has closed ring`() {
        val poly = GeoMath.regularPolygon(GeoPoint(0.0, 0.0), 1.0, 6)
        assertThat(poly.size).isEqualTo(7)
        assertThat(poly.first().latitude).isWithin(1e-6).of(poly.last().latitude)
        assertThat(poly.first().longitude).isWithin(1e-6).of(poly.last().longitude)
    }
}
