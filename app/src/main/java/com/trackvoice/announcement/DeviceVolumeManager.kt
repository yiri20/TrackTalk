package com.trackvoice.announcement

import android.content.Context
import android.media.AudioManager

class DeviceVolumeManager(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var originalVolume: Int? = null

    fun raiseTo(percent: Int) {
        if (originalVolume != null) return
        val stream = AudioManager.STREAM_MUSIC
        val current = audioManager.getStreamVolume(stream)
        val target = (audioManager.getStreamMaxVolume(stream) * percent.coerceIn(10, 100) / 100f).toInt()
        originalVolume = current
        if (target > current) audioManager.setStreamVolume(stream, target, 0)
    }

    fun restore() {
        val original = originalVolume ?: return
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0)
        originalVolume = null
    }
}
