package com.example.e_textilesendingserver.mqtt

import android.content.Context
import com.example.e_textilesendingserver.core.config.BridgeConfig
import com.example.e_textilesendingserver.R
import com.example.e_textilesendingserver.util.await
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLException
import javax.net.ssl.TrustManagerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MqttBridge(
    private val appContext: Context,
) {

    private var client: Mqtt3AsyncClient? = null

    suspend fun connect(config: BridgeConfig) = withContext(Dispatchers.IO) {
        if (client != null) return@withContext

        val useTls = shouldUseTls(config)
        val preferred = buildClient(config, sslMode = if (useTls) SslMode.PINNED_CA else SslMode.NONE)
        try {
            preferred.connectWith()
                .keepAlive(30)
                .cleanSession(true)
                .send()
                .await()
            client = preferred
        } catch (ex: Exception) {
            if (!useTls || !shouldFallbackToSystemTrust(ex)) {
                throw ex
            }
            val fallback = buildClient(config, sslMode = SslMode.SYSTEM_DEFAULT)
            try {
                fallback.connectWith()
                    .keepAlive(30)
                    .cleanSession(true)
                    .send()
                    .await()
                client = fallback
            } catch (fallbackEx: Exception) {
                val insecure = buildClient(config, sslMode = SslMode.INSECURE)
                try {
                    insecure.connectWith()
                        .keepAlive(30)
                        .cleanSession(true)
                        .send()
                        .await()
                    client = insecure
                } catch (insecureEx: Exception) {
                    insecureEx.addSuppressed(fallbackEx)
                    insecureEx.addSuppressed(ex)
                    throw insecureEx
                }
            }
        }
    }

    suspend fun publish(
        topic: String,
        payload: ByteArray,
        qos: Int,
        retain: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        val current = client ?: return@withContext
        current.publishWith()
            .topic(topic)
            .qos(qos.toMqttQos())
            .retain(retain)
            .payload(ByteBuffer.wrap(payload))
            .send()
            .await()
    }

    suspend fun subscribe(
        topic: String,
        scope: CoroutineScope,
        callback: suspend (Mqtt3Publish) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val current = client ?: return@withContext
        current.subscribeWith()
            .topicFilter(topic)
            .callback { publish ->
                scope.launch {
                    callback(publish)
                }
            }
            .send()
            .await()
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        client?.disconnect()?.await()
        client = null
    }

    private fun shouldUseTls(config: BridgeConfig): Boolean = config.brokerPort == 8883

    private enum class SslMode {
        NONE,
        PINNED_CA,
        SYSTEM_DEFAULT,
        INSECURE,
    }

    private fun buildClient(config: BridgeConfig, sslMode: SslMode): Mqtt3AsyncClient {
        val builder = MqttClient.builder()
            .useMqttVersion3()
            .identifier(config.clientId)
            .serverHost(config.brokerHost)
            .serverPort(config.brokerPort)

        when (sslMode) {
            SslMode.NONE -> Unit
            SslMode.SYSTEM_DEFAULT -> builder.sslWithDefaultConfig()
            SslMode.PINNED_CA -> {
                val pinned = runCatching { buildPinnedSslConfig() }.getOrNull()
                if (pinned != null) {
                    builder.sslConfig(pinned)
                } else {
                    builder.sslWithDefaultConfig()
                }
            }
            SslMode.INSECURE -> {
                val insecure = runCatching { buildInsecureSslConfig() }.getOrNull()
                if (insecure != null) {
                    builder.sslConfig(insecure)
                } else {
                    builder.sslWithDefaultConfig()
                }
            }
        }

        return builder
            .automaticReconnectWithDefaultConfig()
            .buildAsync()
    }

    private fun buildPinnedSslConfig(): MqttClientSslConfig {
        val cf = CertificateFactory.getInstance("X.509")
        val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
        }

        // Trust SECOM Root CA 2024
        appContext.resources.openRawResource(R.raw.sc_root_2ca).use { input ->
            val cert = cf.generateCertificate(input)
            store.setCertificateEntry("sc_root", cert)
        }

        // Trust specific server cert (web.crt)
        appContext.resources.openRawResource(R.raw.web).use { input ->
            val cert = cf.generateCertificate(input)
            store.setCertificateEntry("web_cert", cert)
        }

        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(store)
        }
        return MqttClientSslConfig.builder()
            .trustManagerFactory(tmf)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private fun buildInsecureSslConfig(): MqttClientSslConfig {
        val providerName = "InsecureJSSE"
        if (java.security.Security.getProvider(providerName) == null) {
            val provider = object : java.security.Provider(providerName, 1.0, "Insecure JSSE Provider") {
                init {
                    put("TrustManagerFactory.Insecure", InsecureTrustManagerFactory::class.java.name)
                }
            }
            java.security.Security.addProvider(provider)
        }

        val tmf = TrustManagerFactory.getInstance("Insecure", providerName)
        tmf.init(null as KeyStore?)

        return MqttClientSslConfig.builder()
            .trustManagerFactory(tmf)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private fun shouldFallbackToSystemTrust(ex: Throwable): Boolean {
        var current: Throwable? = ex
        while (current != null) {
            if (current is SSLException || current is CertificateException) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private fun Int.toMqttQos(): com.hivemq.client.mqtt.datatypes.MqttQos = when (this) {
        2 -> com.hivemq.client.mqtt.datatypes.MqttQos.EXACTLY_ONCE
        1 -> com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE
        else -> com.hivemq.client.mqtt.datatypes.MqttQos.AT_MOST_ONCE
    }
}

class InsecureTrustManagerFactory : javax.net.ssl.TrustManagerFactorySpi() {
    override fun engineInit(ks: KeyStore?) {}
    override fun engineInit(spec: javax.net.ssl.ManagerFactoryParameters?) {}
    override fun engineGetTrustManagers(): Array<javax.net.ssl.TrustManager> {
        return arrayOf(object : javax.net.ssl.X509TrustManager {
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
        })
    }
}
