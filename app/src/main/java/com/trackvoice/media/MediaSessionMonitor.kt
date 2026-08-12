package com.trackvoice.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.trackvoice.service.TrackVoiceNotificationListenerService

class MediaSessionMonitor(
    context: Context,
    private val onUpdate: (MediaMonitorUpdate) -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(
        appContext,
        TrackVoiceNotificationListenerService::class.java,
    )
    private val handler = Handler(Looper.getMainLooper())
    private val mapper = TrackMetadataMapper(::resolveAppName)
    private val sessions = linkedMapOf<String, TrackedSession>()
    private var started = false
    private var selectedSessionKey: String? = null

    val activeSessionCount: Int get() = sessions.size

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        if (!started) return@OnActiveSessionsChangedListener
        updateControllers(controllers.orEmpty())
        publish(MediaEventType.ACTIVE_SESSIONS)
    }

    fun start() {
        if (started) return
        started = true
        manager.addOnActiveSessionsChangedListener(
            activeSessionsListener,
            listenerComponent,
            handler,
        )
        updateControllers(manager.getActiveSessions(listenerComponent).orEmpty())
        publish(MediaEventType.INITIAL)
    }

    fun stop() {
        if (!started) return
        started = false
        manager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        sessions.values.forEach { tracked ->
            runCatching { tracked.controller.unregisterCallback(tracked.callback) }
        }
        sessions.clear()
        selectedSessionKey = null
    }

    fun refresh() {
        if (!started) return
        updateControllers(manager.getActiveSessions(listenerComponent).orEmpty())
        publish(MediaEventType.ACTIVE_SESSIONS)
    }

    fun pauseSelectedIfPlaying(): PlaybackPauseToken? {
        val tracked = selectedTrackedSession() ?: return null
        val event = mapper.map(tracked.controller)
        if (!event.isPlaying || !event.hasTitle) return null
        val paused = runCatching { tracked.controller.transportControls.pause() }.isSuccess
        return if (paused) {
            PlaybackPauseToken(
                sessionKey = sessionKey(tracked.controller),
                fingerprint = TrackFingerprint.stable(event),
            )
        } else {
            null
        }
    }

    fun resumePlayback(token: PlaybackPauseToken) {
        val tracked = sessions[token.sessionKey] ?: return
        val event = mapper.map(tracked.controller)
        if (TrackFingerprint.stable(event) != token.fingerprint || event.isPlaying) return
        runCatching { tracked.controller.transportControls.play() }
        retryResume(token, 180L)
    }

    fun toggleSelectedPlayback(): Boolean? {
        val tracked = selectedTrackedSession() ?: return null
        val event = mapper.map(tracked.controller)
        return when {
            event.isPlaying -> runCatching {
                tracked.controller.transportControls.pause()
                false
            }.getOrNull()

            event.hasTitle -> runCatching {
                tracked.controller.transportControls.play()
                retryResume(
                    PlaybackPauseToken(
                        sessionKey = sessionKey(tracked.controller),
                        fingerprint = TrackFingerprint.stable(event),
                    ),
                    180L,
                )
                true
            }.getOrNull()

            else -> null
        }
    }

    fun isSelectedPlaybackPlaying(): Boolean? = selectedTrackedSession()?.let {
        mapper.map(it.controller).isPlaying
    }

    private fun updateControllers(controllers: List<MediaController>) {
        val nextKeys = controllers.map { sessionKey(it) }.toSet()
        sessions.keys.toList().filterNot(nextKeys::contains).forEach { key ->
            sessions.remove(key)?.let { tracked ->
                runCatching { tracked.controller.unregisterCallback(tracked.callback) }
            }
        }

        controllers.forEach { controller ->
            val key = sessionKey(controller)
            val existing = sessions[key]
            if (existing == null) {
                val sessionCallback = callbackFor(key)
                runCatching { controller.registerCallback(sessionCallback, handler) }
                    .onFailure { return@forEach }
                val now = System.currentTimeMillis()
                sessions[key] = TrackedSession(controller, sessionCallback, now, now, now)
            } else {
                if (existing.controller !== controller) {
                    runCatching { existing.controller.unregisterCallback(existing.callback) }
                    val sessionCallback = callbackFor(key)
                    runCatching { controller.registerCallback(sessionCallback, handler) }
                        .onFailure { return@forEach }
                    existing.callback = sessionCallback
                }
                existing.controller = controller
            }
        }
    }

    private fun callbackFor(sessionKey: String): MediaController.Callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            publish(MediaEventType.METADATA, sessionKey)
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            publish(MediaEventType.PLAYBACK_STATE, sessionKey)
        }

        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) {
            publish(MediaEventType.QUEUE, sessionKey)
        }

        override fun onSessionDestroyed() {
            if (!started) return
            sessions.remove(sessionKey)?.let { tracked ->
                runCatching { tracked.controller.unregisterCallback(tracked.callback) }
                if (selectedSessionKey == sessionKey) selectedSessionKey = null
                publish(MediaEventType.ACTIVE_SESSIONS)
            }
        }
    }

    private fun publish(eventType: MediaEventType, changedSessionKey: String? = null) {
        if (!started) return
        val now = System.currentTimeMillis()
        changedSessionKey?.let { key ->
            sessions[key]?.let { tracked ->
                when (eventType) {
                    MediaEventType.METADATA -> tracked.lastMetadataChangedAt = now
                    MediaEventType.PLAYBACK_STATE -> tracked.lastPlaybackStateChangedAt = now
                    else -> Unit
                }
                if (eventType != MediaEventType.ACTIVE_SESSIONS) {
                    tracked.lastObservedAt = now
                }
            }
        }

        val mediaKeyToken = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { manager.getMediaKeyEventSession() }.getOrNull()
        } else {
            null
        }
        val snapshots = sessions.map { (key, tracked) ->
            SessionSnapshot(
                sessionKey = key,
                event = mapper.map(tracked.controller, now),
                isMediaKeySession = mediaKeyToken != null && mediaKeyToken == tracked.controller.sessionToken,
                lastMetadataChangedAt = tracked.lastMetadataChangedAt,
                lastPlaybackStateChangedAt = tracked.lastPlaybackStateChangedAt,
                lastObservedAt = tracked.lastObservedAt,
            )
        }
        val selected = ActiveSessionSelector.select(snapshots)
        selectedSessionKey = selected?.sessionKey
        onUpdate(
            MediaMonitorUpdate(
                selected = selected,
                activeSessionCount = sessions.size,
                eventType = eventType,
                observedAt = now,
            ),
        )
    }

    private fun sessionKey(controller: MediaController): String =
        "${controller.packageName}:${controller.sessionToken.hashCode()}"

    private fun selectedTrackedSession(): TrackedSession? =
        selectedSessionKey?.let(sessions::get)
            ?: sessions.values.maxByOrNull { it.lastObservedAt }

    private fun retryResume(token: PlaybackPauseToken, delayMs: Long) {
        handler.postDelayed({
            val tracked = sessions[token.sessionKey] ?: return@postDelayed
            val event = mapper.map(tracked.controller)
            if (TrackFingerprint.stable(event) != token.fingerprint || event.isPlaying) return@postDelayed
            runCatching { tracked.controller.transportControls.play() }
            if (delayMs < 700L) retryResume(token, delayMs * 2)
        }, delayMs)
    }

    private fun resolveAppName(packageName: String): String = runCatching {
        appContext.packageManager.getApplicationLabel(
            appContext.packageManager.getApplicationInfo(packageName, 0),
        ).toString()
    }.getOrDefault(packageName)

    private data class TrackedSession(
        var controller: MediaController,
        var callback: MediaController.Callback,
        var lastMetadataChangedAt: Long,
        var lastPlaybackStateChangedAt: Long,
        var lastObservedAt: Long,
    )
}

data class PlaybackPauseToken(
    val sessionKey: String,
    val fingerprint: String,
)
