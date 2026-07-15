package com.radar.coefficients.domain.usecase

import com.radar.coefficients.domain.model.DemandZone
import com.radar.coefficients.domain.model.GeoPoint
import com.radar.coefficients.domain.model.TariffCoefLabel
import com.radar.coefficients.domain.model.VehicleClass
import com.radar.coefficients.domain.util.GeoMath
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Кэф и прибавка ₽ **точно в точке** (водитель / метка).
 *
 * Правила (чтобы цифры не «врали»):
 * 1) если точка внутри зоны (≤ радиуса) — берём **эту зону целиком** (кэф, ₽, тарифы, улица);
 * 2) если рядом (до ~2 км) — плавный спад к 1.0, без смеси «далёких» зон;
 * 3) если далеко — ×1.0 / +0 ₽, название «Вне зон».
 */
object LocalCoefResolver {

    data class PointSnapshot(
        val districtName: String,
        val labels: List<TariffCoefLabel>,
        val nearestZone: DemandZone?,
        val blendedEconomyCoef: Double,
        val blendedExtraRub: Double,
        val confidence: Double,
        /** true = точка реально в зоне, цифры «как на маркере» */
        val insideZone: Boolean = false
    )

    private val baseMultiplier: Map<VehicleClass, Double> = mapOf(
        VehicleClass.ECONOMY to 1.0,
        VehicleClass.COMFORT to 1.35,
        VehicleClass.COMFORT_PLUS to 1.55,
        VehicleClass.BUSINESS to 2.15,
        VehicleClass.MINIVAN to 1.6,
        VehicleClass.CHILD to 1.4,
        VehicleClass.COURIER to 0.8
    )

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
        val squeeze = if (economy >= 1.8) 0.7 else 1.0
        return round1((economy + offset * squeeze).coerceIn(1.0, 3.0))
    }

    fun extraRubFor(coef: Double, baseIncome: Double, vehicleClass: VehicleClass): Double {
        val mult = baseMultiplier[vehicleClass] ?: 1.0
        val base = baseIncome.coerceAtLeast(100.0) * mult
        return (base * (coef - 1.0).coerceAtLeast(0.0)).coerceAtLeast(0.0)
    }

    fun resolveAt(
        location: GeoPoint?,
        zones: List<DemandZone>,
        visible: Set<VehicleClass>
    ): PointSnapshot {
        val show = visible.ifEmpty { setOf(VehicleClass.ECONOMY) }
        if (zones.isEmpty()) {
            return emptySnap(show, "Нет зон")
        }
        val anchor = location ?: zones.minByOrNull {
            // если нет GPS — центр «самой горячей» рядом с медианой
            -it.heatScore.toDouble()
        }?.center ?: return emptySnap(show, "—")

        // Зона + расстояние до центра + эффективный радиус
        data class Hit(val zone: DemandZone, val distKm: Double, val radiusKm: Double) {
            val inside: Boolean get() = distKm <= radiusKm * 1.02
        }

        val hits = zones.map { z ->
            val r = zoneRadiusKm(z)
            Hit(z, GeoMath.distanceKm(anchor, z.center), r)
        }.sortedWith(
            compareBy<Hit> { !it.inside } // сначала «внутри»
                .thenBy { it.distKm }
        )

        val best = hits.first()
        val zone = best.zone

        return when {
            // 1) Внутри зоны — точные данные зоны (как на маркере)
            best.inside -> {
                val labels = labelsFromZone(zone, show, scale = 1.0)
                PointSnapshot(
                    districtName = zone.districtName,
                    labels = labels,
                    nearestZone = zone,
                    blendedEconomyCoef = round1(zone.coefficient),
                    blendedExtraRub = labels.economyExtra(),
                    confidence = zone.confidence,
                    insideZone = true
                )
            }
            // 2) Рядом с зоной — спад кэфа, без подмешивания чужих районов
            best.distKm <= 2.2 -> {
                val edge = best.radiusKm.coerceAtLeast(0.3)
                // 1 на границе → 0 на ~2.2 км
                val t = ((best.distKm - edge) / (2.2 - edge).coerceAtLeast(0.4)).coerceIn(0.0, 1.0)
                val decay = exp(-2.2 * t) // плавно к 0
                val scale = decay.coerceIn(0.0, 1.0)
                val econ = round1(1.0 + (zone.coefficient - 1.0) * scale)
                val labels = labelsFromZone(zone, show, scale = scale, forceEconomy = econ)
                PointSnapshot(
                    districtName = "≈ ${zone.districtName}",
                    labels = labels,
                    nearestZone = zone,
                    blendedEconomyCoef = econ,
                    blendedExtraRub = labels.economyExtra(),
                    confidence = zone.confidence * scale * 0.85,
                    insideZone = false
                )
            }
            // 3) Далеко — нет смысла показывать чужой кэф
            else -> {
                val labels = VehicleClass.configurable
                    .filter { it in show }
                    .map { TariffCoefLabel(it, 1.0, 0.0) }
                PointSnapshot(
                    districtName = "Вне зон спроса",
                    labels = labels,
                    nearestZone = zone,
                    blendedEconomyCoef = 1.0,
                    blendedExtraRub = 0.0,
                    confidence = 0.2,
                    insideZone = false
                )
            }
        }
    }

    /** Радиус зоны: от центра до вершины полигона (км). */
    fun zoneRadiusKm(zone: DemandZone): Double {
        val poly = zone.polygon
        if (poly.size >= 3) {
            val avg = poly.take(poly.size.coerceAtMost(8))
                .map { GeoMath.distanceKm(zone.center, it) }
                .average()
            if (avg.isFinite() && avg > 0.15) return avg.coerceIn(0.25, 4.0)
        }
        return 0.9
    }

    private fun labelsFromZone(
        zone: DemandZone,
        show: Set<VehicleClass>,
        scale: Double,
        forceEconomy: Double? = null
    ): List<TariffCoefLabel> {
        val econBase = forceEconomy ?: zone.coefficient
        val multi = zone.coefficientsByClass.ifEmpty {
            VehicleClass.configurable.associateWith { tariffCoefFromEconomy(econBase, it) }
        }
        return VehicleClass.configurable
            .filter { it in show }
            .map { cls ->
                val raw = when (cls) {
                    VehicleClass.ECONOMY -> econBase
                    else -> {
                        val zc = multi[cls]
                        if (zc != null && forceEconomy == null && scale >= 0.99) zc
                        else tariffCoefFromEconomy(econBase, cls)
                    }
                }
                val coef = if (scale >= 0.99) {
                    round1(raw)
                } else {
                    round1(1.0 + (raw - 1.0) * scale)
                }
                val extra = when {
                    scale >= 0.99 && cls == VehicleClass.ECONOMY && zone.extraIncome > 0 ->
                        zone.extraIncome
                    else -> extraRubFor(coef, zone.baseIncome, cls)
                }
                TariffCoefLabel(cls, coef, extraRub = extra.roundToInt().toDouble())
            }
    }

    private fun List<TariffCoefLabel>.economyExtra(): Double =
        firstOrNull { it.vehicleClass == VehicleClass.ECONOMY }?.extraRub
            ?: firstOrNull()?.extraRub
            ?: 0.0

    private fun emptySnap(show: Set<VehicleClass>, name: String) = PointSnapshot(
        districtName = name,
        labels = VehicleClass.configurable.filter { it in show }
            .map { TariffCoefLabel(it, 1.0, 0.0) },
        nearestZone = null,
        blendedEconomyCoef = 1.0,
        blendedExtraRub = 0.0,
        confidence = 0.0,
        insideZone = false
    )

    private fun round1(v: Double): Double =
        ((v * 10.0).roundToInt() / 10.0).coerceIn(1.0, 3.0)
}
