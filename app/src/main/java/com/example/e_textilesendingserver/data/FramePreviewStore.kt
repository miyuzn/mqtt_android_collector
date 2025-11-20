package com.example.e_textilesendingserver.data

import com.example.e_textilesendingserver.core.parser.SensorFrame
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Keeps latest frames per device for UI preview (heatmap/metrics).
 */
object FramePreviewStore {
    private val enabledFlag = AtomicBoolean(false)
    private val backing = MutableStateFlow<Map<String, PreviewFrame>>(emptyMap())
    val state: StateFlow<Map<String, PreviewFrame>> = backing

    fun setEnabled(enabled: Boolean) {
        enabledFlag.set(enabled)
        if (!enabled) {
            backing.value = emptyMap()
        }
    }

    fun isEnabled(): Boolean = enabledFlag.get()

    fun onFrame(frame: SensorFrame, receivedAt: Long = System.currentTimeMillis()) {
        if (!enabledFlag.get()) return
        backing.value = backing.value.toMutableMap().apply {
            put(frame.dn, PreviewFrame(frame, receivedAt))
        }
    }

    data class PreviewFrame(
        val frame: SensorFrame,
        val receivedAt: Long,
    )
}
