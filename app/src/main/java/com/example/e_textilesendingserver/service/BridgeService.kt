package com.example.e_textilesendingserver.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.e_textilesendingserver.core.config.BridgeConfigRepository
import kotlinx.coroutines.launch

class BridgeService : LifecycleService() {

    private lateinit var controller: BridgeController
    private lateinit var notificationHelper: BridgeNotificationHelper
    private lateinit var notificationManager: NotificationManager
    
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var multicastLock: android.net.wifi.WifiManager.MulticastLock? = null

    override fun onCreate() {
        super.onCreate()
        val repository = BridgeConfigRepository(applicationContext)
        controller = BridgeController(applicationContext, repository)
        notificationHelper = BridgeNotificationHelper(this)
        notificationHelper.ensureChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        // 使用 WIFI_MODE_FULL_HIGH_PERF 以在屏幕关闭时保持 Wi-Fi 连接活跃（对于 Android 10+ 建议使用 WIFI_MODE_FULL_LOW_LATENCY，但在一般 UDP 场景下 HighPerf 足够且兼容性好）
        wifiLock = wifiManager.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "BridgeService:WifiLock").apply {
            setReferenceCounted(false)
            acquire()
        }
        
        multicastLock = wifiManager.createMulticastLock("BridgeService:MulticastLock").apply {
            setReferenceCounted(false)
            acquire()
        }

        lifecycleScope.launch {
            BridgeStatusStore.state.collect { state ->
                notificationManager.notify(
                    BridgeNotificationHelper.NOTIFICATION_ID,
                    notificationHelper.build(state)
                )
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val initialNotification = notificationHelper.build(BridgeStatusStore.state.value)
        startForeground(BridgeNotificationHelper.NOTIFICATION_ID, initialNotification)
        controller.start()
        return START_STICKY
    }

    override fun onDestroy() {
        controller.stop()
        notificationManager.cancel(BridgeNotificationHelper.NOTIFICATION_ID)
        
        wifiLock?.release()
        multicastLock?.release()
        
        super.onDestroy()
    }
}
