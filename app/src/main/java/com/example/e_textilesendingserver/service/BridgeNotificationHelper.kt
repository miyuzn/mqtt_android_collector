package com.example.e_textilesendingserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.e_textilesendingserver.MainActivity
import com.example.e_textilesendingserver.R

class BridgeNotificationHelper(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun build(state: BridgeState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = when (state) {
            is BridgeState.Running -> {
                val metrics = state.metrics
                if (state.localMode) {
                    context.getString(
                        R.string.bridge_state_running_local_template,
                        metrics.packetsIn,
                        metrics.stored,
                        metrics.deviceCount,
                    )
                } else {
                    context.getString(
                        R.string.bridge_state_running_template,
                        metrics.packetsIn,
                        metrics.parsedPublished,
                        metrics.rawPublished,
                        metrics.deviceCount,
                    )
                }
            }
            is BridgeState.Error -> context.getString(R.string.bridge_state_error, state.message)
            is BridgeState.Starting -> if (state.localMode) {
                context.getString(R.string.bridge_state_starting_local)
            } else {
                context.getString(R.string.bridge_state_starting)
            }
            BridgeState.Idle -> context.getString(R.string.bridge_state_idle)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(pendingIntent)
            .setOngoing(state is BridgeState.Running || state is BridgeState.Starting)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "bridge-channel"
        const val NOTIFICATION_ID = 1001
    }
}
