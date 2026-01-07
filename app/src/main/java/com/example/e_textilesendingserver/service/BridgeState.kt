package com.example.e_textilesendingserver.service

sealed interface BridgeState {
    data object Idle : BridgeState
    data class Starting(val localMode: Boolean) : BridgeState
    data class Running(
        val metrics: BridgeMetrics,
        val localMode: Boolean,
        val connectionError: String? = null,
    ) : BridgeState
    data class Error(val message: String) : BridgeState
}

data class BridgeMetrics(
    val packetsIn: Long,
    val parsedPublished: Long,
    val rawPublished: Long,
    val stored: Long,
    val dropped: Long,
    val parseErrors: Long,
    val deviceCount: Int,
    val lastUpdateMillis: Long,
)
