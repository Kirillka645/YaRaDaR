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
 * Маркеры на карте: машинка «вы здесь» и фиолетовая метка «сюда».
 */
object DriverMarkerFactory {

    fun create(
        context: Context,
        labels: List<TariffCoefLabel>,
        showCoef: Boolean = true,
        showRub: Boolean = true
    ): BitmapDrawable = createMarker(
        context = context,
        labels = labels,
        showCoef = showCoef,
        showRub = showRub,
        emptyTitle = "Вы здесь",
        bubbleColor = Color.parseColor("#0D47A1"),
        markerKind = MarkerKind.CAR
    )

    /** Метка в произвольной точке: кэф + ₽ */
    fun createPin(
        context: Context,
        labels: List<TariffCoefLabel>,
        showCoef: Boolean = true,
        showRub: Boolean = true
    ): BitmapDrawable = createMarker(
        context = context,
        labels = labels,
        showCoef = showCoef,
        showRub = showRub,
        emptyTitle = "Метка",
        bubbleColor = Color.parseColor("#6A1B9A"),
        markerKind = MarkerKind.PIN
    )

    private enum class MarkerKind { CAR, PIN }

    private fun createMarker(
        context: Context,
        labels: List<TariffCoefLabel>,
        showCoef: Boolean,
        showRub: Boolean,
        emptyTitle: String,
        bubbleColor: Int,
        markerKind: MarkerKind
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
        val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bubbleColor
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = dp(3.5f)
        }

        val lines = if (labels.isEmpty()) {
            listOf(emptyTitle, if (showRub && !showCoef) "+0 ₽" else "Э ×1.0")
        } else {
            labels.chunked(2).map { chunk ->
                chunk.joinToString("   ") { it.mapText(showCoef, showRub) }
            }
        }

        val padH = dp(16f)
        val padV = dp(12f)
        val lineH = dp(22f)
        val footH = if (markerKind == MarkerKind.CAR) dp(58f) else dp(42f)
        val footW = if (markerKind == MarkerKind.CAR) dp(80f) else dp(36f)
        val gap = dp(10f)

        var maxTextW = 0f
        lines.forEach { maxTextW = max(maxTextW, titlePaint.measureText(it)) }
        val bubbleW = max(maxTextW + padH * 2, footW + dp(28f))
        val bubbleH = padV * 2 + lineH * lines.size
        val totalW = (max(bubbleW, footW) + dp(16f)).roundToInt().coerceAtLeast(1)
        val totalH = (bubbleH + gap + footH + dp(8f)).roundToInt().coerceAtLeast(1)

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

        val footTop = bubbleH + gap
        if (markerKind == MarkerKind.CAR) {
            drawCar(canvas, cx, footTop, footW, footH, ::dp, borderPaint)
        } else {
            drawPinFoot(canvas, cx, footTop, footH, ::dp, borderPaint)
        }

        return BitmapDrawable(context.resources, bmp).apply {
            setTargetDensity(context.resources.displayMetrics)
            setBounds(0, 0, totalW, totalH)
        }
    }

    private fun drawCar(
        canvas: Canvas,
        cx: Float,
        carTop: Float,
        carW: Float,
        carH: Float,
        dp: (Float) -> Float,
        borderPaint: Paint
    ) {
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
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D50000") }
        canvas.drawCircle(cx, carTop + carH / 2f + dp(4f), dp(5f), pinPaint)
        canvas.drawCircle(cx, carTop + carH / 2f + dp(4f), dp(5f), borderPaint)
    }

    private fun drawPinFoot(
        canvas: Canvas,
        cx: Float,
        top: Float,
        height: Float,
        dp: (Float) -> Float,
        borderPaint: Paint
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AB47BC")
            style = Paint.Style.FILL
        }
        val r = dp(14f)
        val cy = top + r
        canvas.drawCircle(cx, cy, r, fill)
        canvas.drawCircle(cx, cy, r, borderPaint)
        val tip = Path().apply {
            moveTo(cx - dp(10f), cy + dp(6f))
            lineTo(cx, top + height - dp(2f))
            lineTo(cx + dp(10f), cy + dp(6f))
            close()
        }
        canvas.drawPath(tip, fill)
        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, dp(5f), inner)
    }
}
