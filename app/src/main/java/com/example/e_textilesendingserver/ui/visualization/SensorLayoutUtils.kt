package com.example.e_textilesendingserver.ui.visualization

import kotlin.math.abs
import kotlin.math.sqrt

object SensorLayoutUtils {
    private val defaultX = doubleArrayOf(
        -40.6, -21.2, -6.5, 7.2, 17.3,
        -39.6, -24.3, -8.2, 4.3, 15.2,
        -35.3, -23.0, -8.9, 4.5, 16.2,
        -17.0, -8.5, 0.0, 8.5, 17.0,
        -19.5, -11.0, -2.5, 6.0, 14.5,
        -29.0, -19.0, -9.0, 1.0, 11.0,
        -30.0, -20.5, -11.0, -1.5, 8.0,
    )
    private val defaultY = doubleArrayOf(
        -100.5, -104.0, -100.7, -88.0, -73.0,
        -70.8, -68.9, -65.0, -59.8, -54.0,
        -39.0, -36.0, -32.0, -28.5, -25.2,
        0.0, 0.0, 0.0, 0.0, 0.0,
        40.0, 40.0, 40.0, 40.0, 40.0,
        70.0, 70.0, 70.0, 70.0, 70.0,
        90.0, 90.0, 90.0, 90.0, 90.0,
    )

    fun computeLayoutOptions(sensorCount: Int): List<Pair<Int, Int>> {
        if (sensorCount <= 0) return emptyList()
        val pairs = mutableListOf<Pair<Int, Int>>()
        val limit = sqrt(sensorCount.toDouble()).toInt()
        for (r in 1..limit) {
            if (sensorCount % r == 0) {
                val c = sensorCount / r
                if (r <= MAX_DIM && c <= MAX_DIM) {
                    pairs.add(r to c)
                    if (r != c) pairs.add(c to r)
                }
            }
        }
        if (pairs.isEmpty()) pairs.add(minOf(sensorCount, MAX_DIM) to 1)
        return pairs.sortedWith(compareBy<Pair<Int, Int>> { abs(it.first - it.second) }.thenBy { it.first * it.second })
    }

    fun chooseDefaultLayout(sensorCount: Int): Pair<Int, Int> =
        computeLayoutOptions(sensorCount).firstOrNull() ?: (sensorCount to 1)

    fun mapPressuresToGrid(
        pressures: FloatArray,
        rows: Int,
        cols: Int,
        mirrorRows: Boolean,
        mirrorCols: Boolean,
    ): List<Float> {
        val result = ArrayList<Float>(rows * cols)
        repeat(rows * cols) { result.add(0f) }
        val mapped = minOf(pressures.size, rows * cols)
        for (i in 0 until mapped) {
            val r = i / cols
            val c = i % cols
            val mr = if (mirrorRows) rows - 1 - r else r
            val mc = if (mirrorCols) cols - 1 - c else c
            val idx = mr * cols + mc
            result[idx] = pressures[i].toFloat()
        }
        return result
    }

    fun computeCop(
        pressures: FloatArray,
        rows: Int,
        cols: Int,
        mirrorRows: Boolean,
        mirrorCols: Boolean,
    ): CopResult {
        if (pressures.isEmpty()) return CopResult(false, 0f, 0f)
        val adjusted = pressures.map { p -> (p - COP_OFFSET).coerceAtLeast(0f) }.toFloatArray()
        val useDefaultCoords = adjusted.size == defaultX.size
        val cop = if (useDefaultCoords) {
            weightedCop(adjusted, defaultX, defaultY)
        } else {
            gridCop(adjusted, rows, cols, mirrorRows, mirrorCols)
        }
        if (!useDefaultCoords) return cop
        val flippedX = if (mirrorCols) 1f - cop.normX else cop.normX
        val flippedY = if (mirrorRows) 1f - cop.normY else cop.normY
        return cop.copy(normX = flippedX, normY = flippedY)
    }

    private fun weightedCop(
        pressures: FloatArray,
        xCoords: DoubleArray,
        yCoords: DoubleArray,
    ): CopResult {
        var sumW = 0.0
        var sumX = 0.0
        var sumY = 0.0
        val n = minOf(pressures.size, xCoords.size, yCoords.size)
        for (i in 0 until n) {
            val p = pressures[i].toDouble()
            sumW += p
            sumX += p * xCoords[i]
            sumY += p * yCoords[i]
        }
        if (sumW == 0.0) return CopResult(false, 0.5f, 0.5f)
        val cx = (sumX / sumW).toFloat()
        val cy = (sumY / sumW).toFloat()
        return CopResult(true, normalize(cx, xCoords), normalize(cy, yCoords))
    }

    private fun normalize(value: Float, coords: DoubleArray): Float {
        val min = coords.minOrNull()?.toFloat() ?: 0f
        val max = coords.maxOrNull()?.toFloat() ?: 1f
        return if (max == min) 0.5f else (value - min) / (max - min)
    }

    private fun gridCop(
        pressures: FloatArray,
        rows: Int,
        cols: Int,
        mirrorRows: Boolean,
        mirrorCols: Boolean,
    ): CopResult {
        val mapped = minOf(pressures.size, rows * cols)
        var sum = 0.0
        var sumR = 0.0
        var sumC = 0.0
        for (i in 0 until mapped) {
            val r = i / cols
            val c = i % cols
            val mr = if (mirrorRows) rows - 1 - r else r
            val mc = if (mirrorCols) cols - 1 - c else c
            val p = pressures[i].toDouble()
            sum += p
            sumR += p * mr
            sumC += p * mc
        }
        if (sum == 0.0) return CopResult(false, 0.5f, 0.5f)
        val cx = (sumC / sum) / maxOf(1, cols - 1)
        val cy = (sumR / sum) / maxOf(1, rows - 1)
        return CopResult(true, cx.toFloat(), cy.toFloat())
    }

    data class CopResult(
        val hasData: Boolean,
        val normX: Float,
        val normY: Float,
    )

    private const val COP_OFFSET = 250f
    private const val MAX_DIM = 13
}
