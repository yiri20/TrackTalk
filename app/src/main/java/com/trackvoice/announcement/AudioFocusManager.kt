package com.trackvoice.announcement

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

class AudioFocusManager(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { }
    private var focusRequest: AudioFocusRequest? = null

    fun request(duck: Boolean): Boolean {
        // Replace an existing request cleanly when a new TTS batch starts.
        abandon()
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
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }
}
