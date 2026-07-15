package com.radar.coefficients.domain

import com.radar.coefficients.domain.model.CoefficientType
import com.radar.coefficients.domain.model.DemandLevel
import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.RadarSortMode
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.UserSettings
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.usecase.BenefitCalculator
import com.radar.coefficients.domain.usecase.FareCalculator
import com.radar.coefficients.domain.model.FareEstimateRequest
import com.radar.coefficients.domain.model.TaxiTariff
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BenefitCalculatorTest {

    private val driver = GeoPoint(55.75, 37.62)

    private fun zone(
        id: String,
        lat: Double,
        lon: Double,
        coef: Double,
        extra: Double
    ) = DemandZone(
        id = id,
        cityId = "test-city",
        districtName = "Test $id",
        center = GeoPoint(lat, lon),
        polygon = emptyList(),
        coefficient = coef,
        coefficientType = CoefficientType.SURGE,
        baseIncome = 400.0,
        extraIncome = extra,
        fetchedAtEpochMs = System.currentTimeMillis(),
        validUntilEpochMs = System.currentTimeMillis() + 600_000,
        sourceName = "unit-test",
        sourceType = SourceType.DEMO_PROVIDER,
        isRealData = false,
        isDemo = true,
        confidence = 0.5,
        demandLevel = DemandLevel.HIGH,
        availableVehicleClasses = listOf(VehicleClass.ECONOMY),
        survivalProbability = 1.0
    )

    @Test
    fun `higher extra income nearby ranks above far high coefficient when sorting by benefit`() {
        val near = zone("near", 55.76, 37.63, 1.3, 500.0)
        val far = zone("far", 56.20, 38.20, 2.5, 200.0)
        val settings = UserSettings(fuelCostPerKm = 10.0, timeCostPerMinute = 5.0)

        val ranked = BenefitCalculator.rank(
            zones = listOf(far, near),
            driverLocation = driver,
            settings = settings,
            sortMode = RadarSortMode.MAX_BENEFIT
        )

        assertThat(ranked).hasSize(2)
        assertThat(ranked.first().zone.id).isEqualTo("near")
        assertThat(ranked.first().expectedNetBenefit)
            .isGreaterThan(ranked.last().expectedNetBenefit)
    }

    @Test
    fun `sort by highest coefficient ignores travel cost`() {
        val near = zone("near", 55.76, 37.63, 1.2, 800.0)
        val far = zone("far", 56.0, 38.0, 2.4, 50.0)
        val ranked = BenefitCalculator.rank(
            zones = listOf(near, far),
            driverLocation = driver,
            settings = UserSettings(),
            sortMode = RadarSortMode.HIGHEST_COEFFICIENT
        )
        assertThat(ranked.first().zone.coefficient).isEqualTo(2.4)
    }

    @Test
    fun `sort by nearest uses distance`() {
        val near = zone("near", 55.751, 37.621, 1.1, 10.0)
        val far = zone("far", 55.90, 37.90, 3.0, 1000.0)
        val ranked = BenefitCalculator.rank(
            zones = listOf(far, near),
            driverLocation = driver,
            settings = UserSettings(),
            sortMode = RadarSortMode.NEAREST
        )
        assertThat(ranked.first().zone.id).isEqualTo("near")
        assertThat(ranked.first().distanceKm).isLessThan(ranked.last().distanceKm)
    }

    @Test
    fun `benefit subtracts fuel and time costs`() {
        val z = zone("z1", 55.80, 37.70, 1.8, 1000.0)
        val settings = UserSettings(fuelCostPerKm = 10.0, timeCostPerMinute = 10.0)
        val score = BenefitCalculator.score(
            BenefitCalculator.Input(
                zone = z,
                driverLocation = driver,
                settings = settings,
                travelTimeMinutes = 20
            )
        )
        assertThat(score.fuelCost).isGreaterThan(0.0)
        assertThat(score.timeCost).isEqualTo(200.0)
        assertThat(score.expectedNetBenefit).isLessThan(score.expectedGrossExtra)
        assertThat(score.calculationBreakdown.any { it.contains("Комиссия не учтена") }).isTrue()
    }

    @Test
    fun `fare calculator applies coefficient and returns range`() {
        val tariff = TaxiTariff(
            id = "t1",
            cityId = "c1",
            vehicleClass = VehicleClass.ECONOMY,
            minimumFare = 200.0,
            includedDistanceKm = 2.0,
            includedMinutes = 5.0,
            pricePerKm = 10.0,
            pricePerMinute = 5.0,
            fixedPickupPrice = 50.0,
            surcharges = emptyList(),
            currencyCode = "RUB",
            updatedAtEpochMs = 0L,
            sourceName = "test",
            isOfficial = false
        )
        val request = FareEstimateRequest(
            origin = driver,
            destination = GeoPoint(55.8, 37.7),
            vehicleClass = VehicleClass.ECONOMY,
            coefficient = 1.5,
            cityId = "c1",
            distanceKm = 10.0,
            durationMinutes = 20.0
        )
        val estimate = FareCalculator.estimate(tariff, request)
        assertThat(estimate.isApproximate).isTrue()
        assertThat(estimate.range.max).isAtLeast(estimate.range.min)
        assertThat(estimate.coefficientApplied).isEqualTo(1.5)
        assertThat(estimate.range.midpoint())
            .isGreaterThan(estimate.baseWithoutCoefficient.midpoint())
    }
}
