package com.example.e_textilesendingserver.core.parser

import org.json.JSONArray
import org.json.JSONObject

fun SensorFrame.toJsonBytes(): ByteArray {
    val obj = JSONObject().apply {
        put("ts", timestampSeconds)
        put("dn", dn)
        put("sn", sn)
        put("p", JSONArray(pressure.map { it.toDouble() }))
        put("mag", JSONArray(magnetometer.map { it.toDouble() }))
        put("gyro", JSONArray(gyroscope.map { it.toDouble() }))
        put("acc", JSONArray(accelerometer.map { it.toDouble() }))
    }
    // Match Python: always publish as a JSON array (batch)
    return JSONArray().put(obj).toString().toByteArray()
}
