package com.example.e_textilesendingserver.core.config

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.os.Build
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * 简化的配置仓库，后续可接入 DataStore/QR 导入。
 */
class BridgeConfigRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private val defaultId = buildClientId()
    private val defaultLocalRoot = File(
        appContext.getExternalFilesDir(null) ?: appContext.filesDir,
        "mqtt_store"
    ).absolutePath
    private val backing = MutableStateFlow(buildInitialConfig())

    val config: StateFlow<BridgeConfig> = backing

    suspend fun update(block: (BridgeConfig) -> BridgeConfig) {
        val updated = block(backing.value)
        backing.value = updated
        withContext(Dispatchers.IO) {
            persist(updated)
        }
    }

    private fun buildInitialConfig(): BridgeConfig {
        val base = BridgeConfig(
            clientId = defaultId,
            configAgentId = "agent-${defaultId.takeLast(6)}",
            localStoreRoot = defaultLocalRoot,
        )
        val host = prefs.getString(KEY_BROKER_HOST, null)
        val port = prefs.getInt(KEY_BROKER_PORT, -1)
        val localMode = prefs.getBoolean(KEY_LOCAL_MODE, base.localMode)
        val localRoot = (prefs.getString(KEY_LOCAL_ROOT, defaultLocalRoot) ?: defaultLocalRoot)
            .ifBlank { defaultLocalRoot }
        val localFlush = prefs.getInt(KEY_LOCAL_FLUSH_EVERY_ROWS, base.localFlushEveryRows)
        val localInact = prefs.getInt(KEY_LOCAL_INACT_TIMEOUT, base.localInactTimeoutSec)
        val certPath = prefs.getString(KEY_CUSTOM_CA_CERT_PATH, null)
        return base.copy(
            brokerHost = host ?: base.brokerHost,
            brokerPort = if (port > 0) port else base.brokerPort,
            localMode = localMode,
            localStoreRoot = localRoot,
            localFlushEveryRows = localFlush,
            localInactTimeoutSec = localInact,
            customCaCertPath = certPath,
        )
    }

    private fun persist(config: BridgeConfig) {
        prefs.edit()
            .putString(KEY_BROKER_HOST, config.brokerHost)
            .putInt(KEY_BROKER_PORT, config.brokerPort)
            .putBoolean(KEY_LOCAL_MODE, config.localMode)
            .putString(KEY_LOCAL_ROOT, config.localStoreRoot)
            .putInt(KEY_LOCAL_FLUSH_EVERY_ROWS, config.localFlushEveryRows)
            .putInt(KEY_LOCAL_INACT_TIMEOUT, config.localInactTimeoutSec)
            .putString(KEY_CUSTOM_CA_CERT_PATH, config.customCaCertPath)
            .commit()
    }

    private fun buildClientId(): String {
        val model = Build.MODEL.orEmpty().replace("\\s+".toRegex(), "-").lowercase()
        val tail = UUID.randomUUID().toString().takeLast(8)
        return "android-udp-$model-$tail"
    }

    companion object {
        private const val PREFS_NAME = "bridge_config"
        private const val KEY_BROKER_HOST = "broker_host"
        private const val KEY_BROKER_PORT = "broker_port"
        private const val KEY_LOCAL_MODE = "local_mode"
        private const val KEY_LOCAL_ROOT = "local_root"
        private const val KEY_LOCAL_FLUSH_EVERY_ROWS = "local_flush"
        private const val KEY_LOCAL_INACT_TIMEOUT = "local_inact_timeout"
        private const val KEY_CUSTOM_CA_CERT_PATH = "custom_ca_cert_path"
    }
}
