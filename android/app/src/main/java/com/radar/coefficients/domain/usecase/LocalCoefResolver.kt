package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.TariffCoefLabel
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.util.GeoMath
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Считает кэф и прибавку ₽ **в точке водителя**:
 * — берёт 3 ближайшие зоны;
 * — смешивает кэфы по обратному квадрату расстояния (не «прыгает» на границе);
 * — для каждого тарифа — свой кэф и своя прибавка от базы тарифа.
 */
object LocalCoefResolver {

    data class PointSnapshot(
        val districtName: String,
        val labels: List<TariffCoefLabel>,
        val nearestZone: DemandZone?,
        /** Смешанный кэф основного тарифа (Эконом) */
        val blendedEconomyCoef: Double,
        val blendedExtraRub: Double,
        val confidence: Double
    ) {
        fun primaryLine(showCoef: Boolean, showRub: Boolean): String {
            val main = labels.firstOrNull()
                ?: return if (showCoef) "×1.0" else "+0 ₽"
            return main.mapText(showCoef, showRub)
        }

        fun compactLine(showCoef: Boolean, showRub: Boolean, maxTariffs: Int = 3): String =
            labels.take(maxTariffs).joinToString(" · ") { it.mapText(showCoef, showRub) }
    }

    /** Множитель базы тарифа относительно эконома (для прибавки ₽). */
    private val baseMultiplier: Map<VehicleClass, Double> = mapOf(
        VehicleClass.ECONOMY to 1.0,
        VehicleClass.COMFORT to 1.35,
        VehicleClass.COMFORT_PLUS to 1.55,
        VehicleClass.BUSINESS to 2.15,
        VehicleClass.MINIVAN to 1.6,
        VehicleClass.CHILD to 1.4,
        VehicleClass.COURIER to 0.8
    )

    /**
     * Смещение кэфа тарифа относительно эконома (реалистичнее, чем чистый random).
     * Комфорт/бизнес реже «горят» так же сильно; курьер — волатильнее.
     */
    fun tariffCoefFromEconomy(economy: Double, cls: VehicleClass): Double {
        val offset = when (cls) {
            VehicleClass.ECONOMY -> 0.0
            VehicleClass.COMFORT -> -0.08
            VehicleClass.COMFORT_PLUS -> -0.12
            VehicleClass.BUSINESS -> -0.18
            VehicleClass.MINIVAN -> -0.05
            VehicleClass.CHILD -> +0.05
            VehicleClass.COURIER -> +0.12
            VehicleClass.OTHER -> 0.0
        }
        // при сильном surge разрыв тарифов чуть сжимается
        val squeeze = if (economy >= 1.8) 0.7 else 1.0
        return round1((economy + offset * squeeze).coerceIn(1.0, 3.0))
    }

    fun extraRubFor(coef: Double, baseIncome: Double, vehicleClass: VehicleClass): Double {
        val mult = baseMultiplier[vehicleClass] ?: 1.0
        val base = baseIncome * mult
        // прибавка = (кэф−1) × база; минимум 0
        return (base * (coef - 1.0).coerceAtLeast(0.0)).coerceAtLeast(0.0)
    }

    fun resolveAt(
        location: GeoPoint?,
        zones: List<DemandZone>,
        visible: Set<VehicleClass>,
        maxNeighbors: Int = 3
    ): PointSnapshot {
        val show = visible.ifEmpty { setOf(VehicleClass.ECONOMY) }
        if (zones.isEmpty()) {
            val labels = VehicleClass.configurable
                .filter { it in show }
                .map { TariffCoefLabel(it, 1.0, 0.0) }
            return PointSnapshot("—", labels, null, 1.0, 0.0, 0.0)
        }

        val anchor = location ?: zones.first().center
        val ranked = zones
            .map { it to GeoMath.distanceKm(anchor, it.center) }
            .sortedBy { it.second }
        val nearest = ranked.first().first
        val neighbors = ranked.take(maxNeighbors)

        // если стоим почти в центре зоны — берём её без смешивания
        val veryClose = neighbors.first().second < 0.35
        val economyCoef: Double
        val baseIncome: Double
        val conf: Double
        val name: String

        if (veryClose || neighbors.size == 1) {
            economyCoef = nearest.coefficient.coerceIn(1.0, 3.0)
            baseIncome = nearest.baseIncome.coerceAtLeast(200.0)
            conf = nearest.confidence
            name = nearest.districtName
        } else {
            // weight = 1 / (d² + ε)
            val weights = neighbors.map { (_, d) -> 1.0 / (d.pow(2) + 0.15) }
            val sumW = weights.sum().coerceAtLeast(1e-6)
            economyCoef = neighbors.mapIndexed { i, (z, _) ->
                z.coefficient * weights[i]
            }.sum() / sumW
            baseIncome = neighbors.mapIndexed { i, (z, _) ->
                z.baseIncome * weights[i]
            }.sum() / sumW
            conf = neighbors.mapIndexed { i, (z, _) ->
                z.confidence * weights[i]
            }.sum() / sumW
            name = nearest.districtName
        }

        val econ = round1(economyCoef.coerceIn(1.0, 3.0))
        val labels = VehicleClass.configurable
            .filter { it in show }
            .map { cls ->
                // если в ближайшей зоне есть точный кэф тарифа — слегка подмешиваем
                val fromZone = nearest.coefficientsByClass[cls]
                val derived = tariffCoefFromEconomy(econ, cls)
                val coef = when {
                    fromZone != null && veryClose -> round1(fromZone)
                    fromZone != null -> round1(fromZone * 0.45 + derived * 0.55)
                    else -> derived
                }
                val extra = if (nearest.extraIncome > 0 && cls == VehicleClass.ECONOMY && veryClose) {
                    // согласуем с zone.extraIncome для эконома в центре
                    nearest.extraIncome
                } else {
                    extraRubFor(coef, baseIncome, cls)
                }
                TariffCoefLabel(cls, coef, extraRub = extra.roundToInt().toDouble())
            }

        val mainExtra = labels.firstOrNull { it.vehicleClass == VehicleClass.ECONOMY }?.extraRub
            ?: labels.firstOrNull()?.extraRub
            ?: 0.0

        return PointSnapshot(
            districtName = name,
            labels = labels,
            nearestZone = nearest,
            blendedEconomyCoef = econ,
            blendedExtraRub = mainExtra,
            confidence = conf
        )
    }

    private fun round1(v: Double): Double =
        ((v * 10.0).roundToInt() / 10.0).coerceIn(1.0, 3.0)
}
