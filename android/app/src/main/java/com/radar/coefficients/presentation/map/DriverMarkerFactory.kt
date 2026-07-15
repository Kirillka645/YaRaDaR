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
 * Рисует «машинку» + пузырь с тарифами: Э ×1.5 · К ×1.3 · Д ×1.2
 */
object DriverMarkerFactory {

    fun create(context: Context, labels: List<TariffCoefLabel>): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        fun dp(v: Float): Float = v * density

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(13f)
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = dp(11f)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A237E")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(2f)
        }

        val lines = if (labels.isEmpty()) {
            listOf("Нет тарифов")
        } else {
            labels.chunked(2).map { chunk ->
                chunk.joinToString("  ") { it.mapText }
            }
        }

        val padH = dp(10f)
        val padV = dp(8f)
        val lineH = dp(16f)
        val carH = dp(36f)
        val carW = dp(48f)
        val gap = dp(6f)

        var maxTextW = 0f
        lines.forEach { maxTextW = max(maxTextW, titlePaint.measureText(it)) }
        val bubbleW = max(maxTextW + padH * 2, carW + dp(20f))
        val bubbleH = padV * 2 + lineH * lines.size
        val totalW = max(bubbleW, carW).roundToInt() + dp(8f).roundToInt()
        val totalH = (bubbleH + gap + carH + dp(4f)).roundToInt()

        val bmp = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = totalW / 2f

        val bubbleRect = RectF(
            (totalW - bubbleW) / 2f,
            0f,
            (totalW + bubbleW) / 2f,
            bubbleH
        )
        canvas.drawRoundRect(bubbleRect, dp(10f), dp(10f), bubblePaint)
        canvas.drawRoundRect(bubbleRect, dp(10f), dp(10f), borderPaint)

        val path = Path().apply {
            moveTo(cx - dp(8f), bubbleH - 1f)
            lineTo(cx, bubbleH + gap * 0.6f)
            lineTo(cx + dp(8f), bubbleH - 1f)
            close()
        }
        canvas.drawPath(path, bubblePaint)

        var ty = padV + lineH * 0.75f
        lines.forEachIndexed { idx, line ->
            canvas.drawText(line, cx, ty, if (idx == 0) titlePaint else subPaint)
            ty += lineH
        }

        val carTop = bubbleH + gap
        val carLeft = cx - carW / 2f
        val body = RectF(carLeft, carTop + dp(8f), carLeft + carW, carTop + carH - dp(4f))
        val cabin = RectF(
            carLeft + dp(8f),
            carTop + dp(2f),
            carLeft + carW - dp(8f),
            carTop + dp(16f)
        )
        val carPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1565C0")
            style = Paint.Style.FILL
        }
        val wheelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#212121")
        }
        val windowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BBDEFB")
        }
        canvas.drawRoundRect(body, dp(6f), dp(6f), carPaint)
        canvas.drawRoundRect(cabin, dp(4f), dp(4f), carPaint)
        canvas.drawRoundRect(
            RectF(
                cabin.left + dp(2f),
                cabin.top + dp(2f),
                cabin.right - dp(2f),
                cabin.bottom - dp(2f)
            ),
            dp(3f),
            dp(3f),
            windowPaint
        )
        val wheelR = dp(5f)
        canvas.drawCircle(carLeft + dp(12f), carTop + carH - dp(6f), wheelR, wheelPaint)
        canvas.drawCircle(carLeft + carW - dp(12f), carTop + carH - dp(6f), wheelR, wheelPaint)

        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FF5722") }
        canvas.drawCircle(cx, carTop + carH / 2f, dp(3f), pinPaint)

        return BitmapDrawable(context.resources, bmp)
    }
}
