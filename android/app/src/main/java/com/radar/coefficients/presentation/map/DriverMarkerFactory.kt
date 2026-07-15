package com.radar.coefficients.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import com.radar.coefficients.domain.model.TariffCoefLabel
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Крупная машинка + пузырь сверху: Э ×1.5 +180 ₽
 */
object DriverMarkerFactory {

    fun create(
        context: Context,
        labels: List<TariffCoefLabel>,
        showCoef: Boolean = true,
        showRub: Boolean = true
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float): Float = v * density

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(16f)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(14f)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        // без setShadowLayer — на части устройств тень ломает bitmap/hardware layer
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0D47A1")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(3.5f)
        }

        val lines = if (labels.isEmpty()) {
            listOf("Вы здесь", if (showRub && !showCoef) "+0 ₽" else "Э ×1.0")
        } else {
            // по 1–2 тарифа в строке
            labels.chunked(2).map { chunk ->
                chunk.joinToString("   ") { it.mapText(showCoef, showRub) }
            }
        }

        val padH = dp(16f)
        val padV = dp(12f)
        val lineH = dp(22f)
        val carH = dp(58f)
        val carW = dp(80f)
        val gap = dp(10f)

        var maxTextW = 0f
        lines.forEach { maxTextW = max(maxTextW, titlePaint.measureText(it)) }
        val bubbleW = max(maxTextW + padH * 2, carW + dp(28f))
        val bubbleH = padV * 2 + lineH * lines.size
        val totalW = (max(bubbleW, carW) + dp(16f)).roundToInt().coerceAtLeast(1)
        val totalH = (bubbleH + gap + carH + dp(8f)).roundToInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        bmp.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(bmp)
        val cx = totalW / 2f

        val bubbleRect = RectF(
            (totalW - bubbleW) / 2f,
            dp(2f),
            (totalW + bubbleW) / 2f,
            bubbleH + dp(2f)
        )
        canvas.drawRoundRect(bubbleRect, dp(12f), dp(12f), bubblePaint)
        canvas.drawRoundRect(bubbleRect, dp(12f), dp(12f), borderPaint)

        val path = Path().apply {
            moveTo(cx - dp(10f), bubbleH)
            lineTo(cx, bubbleH + gap * 0.7f)
            lineTo(cx + dp(10f), bubbleH)
            close()
        }
        canvas.drawPath(path, bubblePaint)

        var ty = padV + lineH * 0.8f + dp(2f)
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, cx, ty, if (idx == 0) titlePaint else subPaint)
            ty += lineH
        }

        val carTop = bubbleH + gap
        val carLeft = cx - carW / 2f
        val body = RectF(carLeft, carTop + dp(12f), carLeft + carW, carTop + carH - dp(6f))
        val cabin = RectF(
            carLeft + dp(12f),
            carTop + dp(2f),
            carLeft + carW - dp(12f),
            carTop + dp(22f)
        )
        val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF6D00")
            style = Paint.Style.FILL
        }
        val carStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
        }
        val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#212121")
        }
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3F2FD")
        }
        canvas.drawRoundRect(body, dp(8f), dp(8f), carPaint)
        canvas.drawRoundRect(body, dp(8f), dp(8f), carStroke)
        canvas.drawRoundRect(cabin, dp(6f), dp(6f), carPaint)
        canvas.drawRoundRect(cabin, dp(6f), dp(6f), carStroke)
        canvas.drawRoundRect(
            RectF(
                cabin.left + dp(3f),
                cabin.top + dp(3f),
                cabin.right - dp(3f),
                cabin.bottom - dp(3f)
            ),
            dp(4f),
            dp(4f),
            windowPaint
        )
        val wheelR = dp(7f)
        canvas.drawCircle(carLeft + dp(16f), carTop + carH - dp(8f), wheelR, wheelPaint)
        canvas.drawCircle(carLeft + carW - dp(16f), carTop + carH - dp(8f), wheelR, wheelPaint)
        // оранжевая точка «вы здесь»
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D50000") }
        canvas.drawCircle(cx, carTop + carH / 2f + dp(4f), dp(5f), pinPaint)
        canvas.drawCircle(cx, carTop + carH / 2f + dp(4f), dp(5f), borderPaint)

        return BitmapDrawable(context.resources, bmp).apply {
            setTargetDensity(context.resources.displayMetrics)
            setBounds(0, 0, totalW, totalH)
        }
    }
}
