package com.radar.coefficients.data.repository

import com.radar.coefficients.data.provider.TariffFareEstimateProvider
import com.radar.coefficients.domain.model.TaxiTariff
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.repository.TariffRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TariffRepositoryImpl @Inject constructor(
    private val fareProvider: TariffFareEstimateProvider
) : TariffRepository {
    override suspend fun getTariffs(cityId: String): List<TaxiTariff> =
        fareProvider.getAvailableTariffs(cityId)

    override suspend fun getTariff(cityId: String, vehicleClass: VehicleClass): TaxiTariff? =
        fareProvider.getTariffDetails(cityId, vehicleClass)
}
