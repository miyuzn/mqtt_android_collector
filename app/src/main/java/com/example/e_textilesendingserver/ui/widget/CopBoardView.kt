package com.example.e_textilesendingserver.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class CopBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var hasData = false
    private var normX = 0.5f
    private var normY = 0.5f
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.LTGRAY
        strokeWidth = 1f
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(13, 110, 253)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.DKGRAY
        strokeWidth = 2f
    }

    fun setCop(normX: Float, normY: Float, hasData: Boolean) {
        this.normX = normX.coerceIn(0f, 1f)
        this.normY = normY.coerceIn(0f, 1f)
        this.hasData = hasData
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val left = (width - size) / 2f
        val top = (height - size) / 2f
        val cell = size / 8f
        for (i in 0..8) {
            val x = left + i * cell
            canvas.drawLine(x, top, x, top + size, gridPaint)
            val y = top + i * cell
            canvas.drawLine(left, y, left + size, y, gridPaint)
        }
        canvas.drawRect(left, top, left + size, top + size, borderPaint)
        if (hasData) {
            val cx = left + normX * size
            val cy = top + normY * size
            canvas.drawCircle(cx, cy, size * 0.03f, dotPaint)
        }
    }
}
