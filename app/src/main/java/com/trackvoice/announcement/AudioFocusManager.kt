package com.trackvoice.announcement

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.trackvoice.diagnostics.TrackTalkDebugLog

class AudioFocusManager(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var focusRequest: AudioFocusRequest? = null

    fun request(duck: Boolean): Boolean {
        // Replace an existing request cleanly when a new TTS batch starts.
        abandon()
        return runCatching {
            val request = AudioFocusRequest.Builder(
                if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = request
            val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            TrackTalkDebugLog.event("audio_focus", "action" to "request", "duck" to duck, "granted" to granted)
            granted
        }.getOrDefault(false)
    }

    fun abandon() {
        if (focusRequest != null) TrackTalkDebugLog.event("audio_focus", "action" to "abandon")
        runCatching { focusRequest?.let(audioManager::abandonAudioFocusRequest) }
        focusRequest = null
    }
}
