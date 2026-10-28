package com.example.hellowear

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class WeatherChartView(
    context: Context,
    private val points: List<WeatherPoint>,
    private val useCelsius: Boolean
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density
    private fun temp(f: Double): Int = if (useCelsius) ((f - 32.0) * 5.0 / 9.0).roundToInt() else f.roundToInt()

    init {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(264f).toInt())
        contentDescription = "Temperature curve and precipitation chance for ${points.size} route points"
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.style = Paint.Style.FILL
        paint.color = 0xFF15191D.toInt()
        canvas.drawRoundRect(RectF(0f, 0f, w, h), dp(20f), dp(20f), paint)
        if (points.isEmpty()) return

        val left = dp(60f); val right = w - dp(15f)
        val top = dp(32f); val tempBottom = dp(112f)
        val rainTop = dp(138f); val rainBottom = dp(172f)
        val count = points.size
        val xs = List(count) { i -> if (count == 1) (left + right) / 2f else left + (right - left) * i / (count - 1) }

        paint.color = 0xFF333B40.toInt(); paint.strokeWidth = dp(1f); paint.style = Paint.Style.STROKE
        for (step in 0..2) {
            val y = top + (tempBottom - top) * step / 2f
            canvas.drawLine(left, y, right, y, paint)
        }
        paint.style = Paint.Style.FILL
        val minF = points.minOf { it.temperatureF }; val maxF = points.maxOf { it.temperatureF }
        val range = max(4.0, maxF - minF)
        val low = minF - (range * 0.15); val high = maxF + (range * 0.15)
        val ys = points.map { p -> tempBottom - ((p.temperatureF - low) / (high - low)).toFloat() * (tempBottom - top) }
        paint.color = 0xFF83D6BE.toInt(); paint.strokeWidth = dp(3f); paint.style = Paint.Style.STROKE
        val curve = Path().apply { moveTo(xs.first(), ys.first()) }
        for (i in 1 until count) {
            val midX = (xs[i - 1] + xs[i]) / 2f
            curve.cubicTo(midX, ys[i - 1], midX, ys[i], xs[i], ys[i])
        }
        canvas.drawPath(curve, paint)
        paint.style = Paint.Style.FILL
        points.forEachIndexed { i, point ->
            paint.color = 0xFFB4F1DE.toInt(); canvas.drawCircle(xs[i], ys[i], dp(4f), paint)
            paint.color = 0xFFE9FFF8.toInt(); paint.textSize = dp(10f); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${temp(point.temperatureF)}°${if (useCelsius) "C" else "F"}", xs[i], ys[i] - dp(8f), paint)
        }

        paint.color = 0xFFAFB9B5.toInt(); paint.textAlign = Paint.Align.LEFT; paint.textSize = dp(9f)
        canvas.drawText("TEMP", dp(13f), dp(18f), paint)
        canvas.drawText("RAIN", dp(13f), rainTop + dp(11f), paint)
        paint.color = 0xFF2E3738.toInt(); canvas.drawLine(left, rainBottom, right, rainBottom, paint)
        val slot = (right - left) / count
        points.forEachIndexed { i, point ->
            val barWidth = min(dp(18f), max(dp(7f), slot * 0.45f))
            val barHeight = (rainBottom - rainTop - dp(4f)) * point.rainChance.coerceIn(0, 100) / 100f
            val x = xs[i] - barWidth / 2f
            paint.color = if (point.rainChance > 0) 0xFFFF8A80.toInt() else 0xFF4B625A.toInt()
            canvas.drawRoundRect(RectF(x, rainBottom - barHeight, x + barWidth, rainBottom), dp(4f), dp(4f), paint)
            paint.color = 0xFFE6E9E7.toInt(); paint.textSize = dp(8f); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("${point.rainChance}%", xs[i], rainBottom + dp(11f), paint)
            paint.color = 0xFFB1BAB7.toInt(); paint.textSize = dp(8f)
            canvas.drawText(point.time, xs[i], dp(203f), paint)
        }

        paint.color = 0xFFAFB9B5.toInt(); paint.textAlign = Paint.Align.LEFT; paint.textSize = dp(8f)
        canvas.drawText("WIND mph", dp(8f), dp(228f), paint)
        canvas.drawText("GUST mph", dp(8f), dp(249f), paint)
        paint.color = 0xFFE6E9E7.toInt(); paint.textAlign = Paint.Align.CENTER; paint.textSize = dp(9f)
        points.forEachIndexed { i, point ->
            canvas.drawText(point.windMph.roundToInt().toString(), xs[i], dp(228f), paint)
            canvas.drawText(point.gustMph.roundToInt().toString(), xs[i], dp(249f), paint)
        }
    }
}
