package com.trackvoice.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.trackvoice.MainActivity
import com.trackvoice.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.trackvoice.TrackVoiceApplication
import com.trackvoice.diagnostics.TrackTalkDebugLog

class TrackVoiceNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val controller = application.controller
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> controller.onScreenOff()
                Intent.ACTION_SCREEN_ON -> controller.onScreenOn()
            }
        }
    }
    private var receiverRegistered = false

    private val application: TrackVoiceApplication
        get() = getApplication() as TrackVoiceApplication

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.google.android.apps.youtube.music") return
        val extras = sbn.notification.extras
        TrackTalkDebugLog.event(
            "NOTIFICATION_MEDIA_EVIDENCE",
            "package" to sbn.packageName,
            "category" to sbn.notification.category,
            "ongoing" to sbn.isOngoing,
            "extras" to extras?.keySet()?.sorted()?.joinToString(",", prefix = "[", postfix = "]"),
            "trackLikeCandidates" to extras.trackLikeNumericCandidates(),
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        application.controller.attachNotificationListener()
        application.controller.attachMediaSessionMonitor(this)
        observeShortcutNotification()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_OFF)
                    addAction(Intent.ACTION_SCREEN_ON)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onListenerDisconnected() {
        application.controller.detachNotificationListener(preservePlaybackHistory = true)
        notificationJob?.cancel()
        notificationManager.cancel(SHORTCUT_NOTIFICATION_ID)
        unregisterScreenReceiver()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        application.controller.detachNotificationListener(preservePlaybackHistory = true)
        notificationJob?.cancel()
        scope.cancel()
        notificationManager.cancel(SHORTCUT_NOTIFICATION_ID)
        unregisterScreenReceiver()
        super.onDestroy()
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private fun observeShortcutNotification() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                SHORTCUT_CHANNEL_ID,
                "TrackTalk 바로가기",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        notificationJob?.cancel()
        notificationJob = scope.launch {
            application.repository.userSettings.collectLatest { settings ->
                if (settings.showStatusNotification) showShortcutNotification(settings.enabled)
                else notificationManager.cancel(SHORTCUT_NOTIFICATION_ID)
            }
        }
    }

    private fun showShortcutNotification(enabled: Boolean) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, SHORTCUT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trackvoice)
            .setContentTitle(if (enabled) "TrackTalk 켜짐" else "TrackTalk 꺼짐")
            .setContentText("눌러서 설정 열기")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        notificationManager.notify(SHORTCUT_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val SHORTCUT_CHANNEL_ID = "trackvoice_shortcut"
        const val SHORTCUT_NOTIFICATION_ID = 2101
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        receiverRegistered = false
    }

    private fun Bundle?.trackLikeNumericCandidates(): String = this
        ?.keySet()
        ?.asSequence()
        ?.filter { key ->
            val normalized = key.lowercase(java.util.Locale.ROOT)
            normalized.contains("track") ||
                normalized.contains("disc") ||
                normalized.contains("number") ||
                normalized.contains("position") ||
                normalized.contains("index")
        }
        ?.sorted()
        ?.mapNotNull { key ->
            runCatching {
                when (val value = get(key)) {
                    is Byte, is Short, is Int, is Long, is Float, is Double -> "$key=$value"
                    is String -> value.trim().toLongOrNull()?.let { "$key=$it" }
                    else -> null
                }
            }.getOrNull()
        }
        ?.joinToString(",")
        .orEmpty()
}
