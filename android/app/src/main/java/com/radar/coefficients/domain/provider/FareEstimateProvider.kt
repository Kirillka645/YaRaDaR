package com.radar.coefficients.domain.provider

import com.radar.coefficients.domain.model.FareEstimate
import com.radar.coefficients.domain.model.FareEstimateRequest
import com.radar.coefficients.domain.model.ProviderStatus
import com.radar.coefficients.domain.model.TaxiTariff
import com.radar.coefficients.domain.model.VehicleClass

interface FareEstimateProvider {
    val providerId: String

    suspend fun getAvailableTariffs(cityId: String): List<TaxiTariff>
    suspend fun getTariffDetails(cityId: String, vehicleClass: VehicleClass): TaxiTariff?
    suspend fun estimateFare(request: FareEstimateRequest): FareEstimate
    suspend fun getFareProviderStatus(): ProviderStatus
    suspend fun getLastUpdatedAt(): Long?
}
