package com.trackvoice.announcement

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.trackvoice.diagnostics.TrackTalkDebugLog

class AudioFocusManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        TrackTalkDebugLog.event(
            "audio_focus",
            "action" to "change",
            "focusChange" to change,
        )
    }
    private var focusRequest: AudioFocusRequest? = null

    fun request(duck: Boolean): Boolean {
        // Replace an existing request cleanly when a new TTS batch starts.
        abandon()
        return runCatching {
            val request = AudioFocusRequest.Builder(
                if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
                .setAudioAttributes(TrackTalkAudioAttributes.speech())
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = request
            val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            TrackTalkDebugLog.event(
                "audio_focus",
                "action" to "request",
                "duck" to duck,
                "granted" to granted,
                "audioFocusMode" to if (duck) "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK" else "AUDIOFOCUS_GAIN_TRANSIENT",
                "usage" to TrackTalkAudioAttributes.USAGE_LABEL,
                "contentType" to TrackTalkAudioAttributes.CONTENT_TYPE_LABEL,
                "musicStreamVolumeBefore" to runCatching {
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }.getOrNull(),
                "musicStreamMaxVolume" to runCatching {
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                }.getOrNull(),
                "ttsVolumeControlStream" to TrackTalkAudioAttributes.speech().volumeControlStream,
                "ttsStreamVolumeBefore" to runCatching {
                    audioManager.getStreamVolume(TrackTalkAudioAttributes.speech().volumeControlStream)
                }.getOrNull(),
                "outputDeviceTypes" to runCatching {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .map { it.type }
                        .distinct()
                        .sorted()
                }.getOrNull(),
            )
            granted
        }.getOrDefault(false)
    }

    fun abandon() {
        if (focusRequest != null) {
            TrackTalkDebugLog.event(
                "audio_focus",
                "action" to "abandon",
                "musicStreamVolumeAfter" to runCatching {
                    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }.getOrNull(),
                "ttsStreamVolumeAfter" to runCatching {
                    audioManager.getStreamVolume(TrackTalkAudioAttributes.speech().volumeControlStream)
                }.getOrNull(),
                "outputDeviceTypes" to runCatching {
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .map { it.type }
                        .distinct()
                        .sorted()
                }.getOrNull(),
            )
        }
        runCatching { focusRequest?.let(audioManager::abandonAudioFocusRequest) }
        focusRequest = null
    }
}
