package com.radar.coefficients.data.provider

import com.radar.coefficients.domain.model.FareEstimate
import com.radar.coefficients.domain.model.FareEstimateRequest
import com.radar.coefficients.domain.model.ProviderConnectionStatus
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.model.SourceType
import com.radar.coefficients.domain.model.TaxiTariff
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.provider.FareEstimateProvider
import com.radar.coefficients.domain.usecase.FareCalculator
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds approximate fares from configured/demo tariffs for the selected city.
 * Not an official pre-estimate API.
 */
@Singleton
class TariffFareEstimateProvider @Inject constructor() : FareEstimateProvider {

    override val providerId: String = "tariff_calc"

    private val tariffs = ConcurrentHashMap<String, List<TaxiTariff>>()
    private var lastUpdated: Long? = null

    fun setTariffsForCity(cityId: String, list: List<TaxiTariff>) {
        tariffs[cityId] = list
        lastUpdated = System.currentTimeMillis()
    }

    fun ensureDefaultTariffs(cityId: String, currencyCode: String) {
        if (tariffs.containsKey(cityId)) return
        val now = System.currentTimeMillis()
        val scale = when (currencyCode) {
            "RUB" -> 1.0
            "KZT" -> 5.0
            "BYN" -> 0.035
            "EUR" -> 0.012
            "USD" -> 0.013
            else -> 1.0
        }
        fun t(
            id: String,
            cls: VehicleClass,
            min: Double,
            perKm: Double,
            perMin: Double,
            pickup: Double
        ) = TaxiTariff(
            id = "$cityId-$id",
            cityId = cityId,
            vehicleClass = cls,
            minimumFare = min * scale,
            includedDistanceKm = 2.0,
            includedMinutes = 5.0,
            pricePerKm = perKm * scale,
            pricePerMinute = perMin * scale,
            fixedPickupPrice = pickup * scale,
            surcharges = emptyList(),
            currencyCode = currencyCode,
            updatedAtEpochMs = now,
            sourceName = "Демо-тариф (не официальный)",
            isOfficial = false
        )
        tariffs[cityId] = listOf(
            t("eco", VehicleClass.ECONOMY, 199.0, 12.0, 6.0, 49.0),
            t("comf", VehicleClass.COMFORT, 279.0, 16.0, 8.0, 69.0),
            t("comfp", VehicleClass.COMFORT_PLUS, 349.0, 20.0, 10.0, 89.0),
            t("biz", VehicleClass.BUSINESS, 599.0, 35.0, 18.0, 149.0),
            t("van", VehicleClass.MINIVAN, 399.0, 22.0, 12.0, 99.0)
        )
        lastUpdated = now
    }

    override suspend fun getAvailableTariffs(cityId: String): List<TaxiTariff> =
        tariffs[cityId].orEmpty()

    override suspend fun getTariffDetails(cityId: String, vehicleClass: VehicleClass): TaxiTariff? =
        tariffs[cityId]?.firstOrNull { it.vehicleClass == vehicleClass }

    override suspend fun estimateFare(request: FareEstimateRequest): FareEstimate {
        val tariff = getTariffDetails(request.cityId, request.vehicleClass)
            ?: getAvailableTariffs(request.cityId).firstOrNull()
            ?: error("Нет тарифа для города ${request.cityId}")
        return FareCalculator.estimate(tariff, request)
    }

    override suspend fun getFareProviderStatus(): ProviderStatus = ProviderStatus(
        name = "Расчёт по тарифу",
        sourceType = SourceType.DEMO_PROVIDER,
        status = ProviderConnectionStatus.CONNECTED,
        lastUpdatedAtEpochMs = lastUpdated,
        supportedCitiesHint = "Любой город с загруженными тарифами",
        termsOfUse = "Приблизительный расчёт. Не гарантирует цену заказа.",
        isDemo = true
    )

    override suspend fun getLastUpdatedAt(): Long? = lastUpdated
}
