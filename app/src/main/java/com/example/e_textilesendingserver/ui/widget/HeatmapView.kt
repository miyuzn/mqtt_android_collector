package com.example.e_textilesendingserver.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var rows: Int = 1
    private var cols: Int = 1
    private var values: List<Float> = emptyList()
    private var minValue: Float = 0f
    private var maxValue: Float = 1f
    private val cellRect = RectF()
    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(60, 0, 0, 0)
    }

    fun setData(rows: Int, cols: Int, values: List<Float>, minValue: Float, maxValue: Float) {
        this.rows = max(1, rows)
        this.cols = max(1, cols)
        this.values = values
        this.minValue = minValue
        this.maxValue = max(maxValue, minValue + 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellW = width.toFloat() / cols
        val cellH = height.toFloat() / rows
        repeat(rows) { r ->
            repeat(cols) { c ->
                val idx = r * cols + c
                val v = values.getOrNull(idx) ?: 0f
                cellPaint.color = colorForValue(v)
                cellRect.set(
                    c * cellW,
                    r * cellH,
                    (c + 1) * cellW,
                    (r + 1) * cellH,
                )
                canvas.drawRect(cellRect, cellPaint)
                canvas.drawRect(cellRect, gridPaint)
            }
        }
    }

    private fun colorForValue(value: Float): Int {
        val t = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
        return lerpColor(BLUE, YELLOW, RED, t)
    }

    private fun lerpColor(a: Int, b: Int, c: Int, t: Float): Int {
        val t2 = if (t < 0.5f) t * 2f else (t - 0.5f) * 2f
        return if (t < 0.5f) mixColor(a, b, t2) else mixColor(b, c, t2)
    }

    private fun mixColor(a: Int, b: Int, t: Float): Int {
        val r = (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt()
        val g = (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt()
        val bl = (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
        return Color.rgb(r, g, bl)
    }

    companion object {
        private val BLUE = Color.rgb(19, 130, 211)
        private val YELLOW = Color.rgb(255, 201, 71)
        private val RED = Color.rgb(220, 53, 69)
    }
}
