package com.example.e_textilesendingserver.data

import com.example.e_textilesendingserver.core.parser.SensorFrame
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local CSV sink that mirrors app/sink.py behavior:
 * - root/<DN>/<YYYYMMDD>/<HHmmss>.csv
 * - first line: // DN: <dn>, SN: <sn>
 * - header row: Timestamp, P1..Psn, Mag_x..Acc_z
 */
class LocalStore(
    rootDir: File,
    private val flushEveryRows: Int,
    private val inactivityTimeoutSec: Int,
    private val zoneId: ZoneId = ZoneId.of("Asia/Tokyo"),
) : Closeable {

    private val root = rootDir.canonicalFile
    private val sessions = linkedMapOf<String, Session>()
    private val formatterDay = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val formatterTime = DateTimeFormatter.ofPattern("HHmmss")

    val rootPath: String = root.absolutePath

    suspend fun write(frame: SensorFrame) = withContext(Dispatchers.IO) {
        val ingestTime = ZonedDateTime.now(zoneId)
        val eventTime = resolveEventTime(frame.timestampSeconds, ingestTime)
        val dnHex = frame.dn.ifBlank { "000000000000" }
        val snValue = max(1, frame.sn)
        val session = ensureSession(dnHex, snValue, eventTime)
        session.handle.writeRow(
            ts = frame.timestampSeconds,
            pressures = frame.pressure,
            mag = frame.magnetometer,
            gyro = frame.gyroscope,
            acc = frame.accelerometer,
            flushEvery = flushEveryRows,
        )
        session.lastSeen = eventTime
    }

    override fun close() {
        sessions.values.forEach { it.handle.close() }
        sessions.clear()
    }

    private fun resolveEventTime(ts: Double, fallback: ZonedDateTime): ZonedDateTime {
        if (ts.isNaN() || ts.isInfinite() || ts <= 0.0) return fallback
        return try {
            ZonedDateTime.ofInstant(Instant.ofEpochMilli((ts * 1000.0).toLong()), zoneId)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun ensureSession(dnHex: String, sn: Int, whenTime: ZonedDateTime): Session {
        val day = formatterDay.format(whenTime)
        val existing = sessions[dnHex]
        if (existing != null) {
            val idleSec = max(0L, whenTime.toEpochSecond() - existing.lastSeen.toEpochSecond())
            val snChanged = existing.sn != sn
            if (existing.day != day || idleSec >= inactivityTimeoutSec || snChanged) {
                existing.handle.close()
            } else {
                return existing
            }
        }
        val handle = CsvHandle(newHandlePath(dnHex, whenTime), sn, dnHex)
        val created = Session(
            day = day,
            handle = handle,
            lastSeen = whenTime,
            sn = sn,
        )
        sessions[dnHex] = created
        return created
    }

    private fun newHandlePath(dnHex: String, whenTime: ZonedDateTime): File {
        val day = formatterDay.format(whenTime)
        val timeStr = formatterTime.format(whenTime)
        return File(root, "$dnHex/$day/$timeStr.csv")
    }

    private data class Session(
        val day: String,
        val handle: CsvHandle,
        var lastSeen: ZonedDateTime,
        var sn: Int,
    )
}

private class CsvHandle(
    private val path: File,
    private val sn: Int,
    private val dnHex: String,
) : Closeable {

    private var writer: PrintWriter? = null
    private var rowsSinceFlush = 0

    fun writeRow(
        ts: Double,
        pressures: FloatArray,
        mag: FloatArray,
        gyro: FloatArray,
        acc: FloatArray,
        flushEvery: Int,
    ) {
        ensureOpen()
        val w = writer ?: return
        val row = buildRow(ts, pressures, mag, gyro, acc)
        w.println(row)
        rowsSinceFlush += 1
        if (rowsSinceFlush >= flushEvery) {
            w.flush()
            rowsSinceFlush = 0
        }
    }

    private fun buildRow(
        ts: Double,
        pressures: FloatArray,
        mag: FloatArray,
        gyro: FloatArray,
        acc: FloatArray,
    ): String {
        val builder = StringBuilder()
        builder.append(ts)
        repeat(sn) { idx ->
            val value = if (idx in pressures.indices) pressures[idx] else 0f
            builder.append(',').append(value.toStringSafe())
        }
        fun appendTriple(arr: FloatArray) {
            repeat(3) { i ->
                val value = if (i in arr.indices) arr[i] else 0f
                builder.append(',').append(value.toStringSafe())
            }
        }
        appendTriple(mag)
        appendTriple(gyro)
        appendTriple(acc)
        return builder.toString()
    }

    private fun ensureOpen() {
        if (writer != null) return
        path.parentFile?.mkdirs()
        val isNew = !path.exists()
        writer = PrintWriter(FileWriter(path, true))
        if (isNew) {
            writeHeader()
        }
    }

    private fun writeHeader() {
        val w = writer ?: return
        w.println("// DN: $dnHex, SN: $sn")
        val header = buildString {
            append("Timestamp")
            repeat(sn) { idx -> append(",P").append(idx + 1) }
            append(",Mag_x,Mag_y,Mag_z,Gyro_x,Gyro_y,Gyro_z,Acc_x,Acc_y,Acc_z")
        }
        w.println(header)
    }

    private fun Float.toStringSafe(): String = String.format(Locale.US, "%.6f", this.toDouble())

    override fun close() {
        writer?.flush()
        writer?.close()
        writer = null
    }
}
