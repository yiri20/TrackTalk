package com.trackvoice.announcement

import android.content.Context
import android.media.AudioManager
import com.trackvoice.data.MAX_MUSIC_DUCK_PERCENT
import com.trackvoice.data.MIN_MUSIC_DUCK_PERCENT
import com.trackvoice.diagnostics.TrackTalkDebugLog
import kotlin.math.roundToInt

object MusicVolumeCalculator {
    fun targetVolume(currentVolume: Int, maxVolume: Int, duckPercent: Int): Int {
        if (maxVolume <= 0) return 0
        val calculated = (currentVolume * duckPercent.coerceIn(MIN_MUSIC_DUCK_PERCENT, MAX_MUSIC_DUCK_PERCENT) / 100f)
            .roundToInt()
            .coerceIn(0, maxVolume)
        // A low but audible music volume must not become a hard mute while the
        // voice guide is playing. restore() puts the exact original value back
        // after the announcement.
        return if (currentVolume > 0) calculated.coerceAtLeast(1) else calculated
    }
}

/** Temporarily lowers the shared media stream and restores it after the announcement. */
class MusicVolumeManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var originalVolume: Int? = null
    private var duckedVolume: Int? = null

    init {
        recoverInterruptedDuck()
    }

    @Synchronized
    fun duckTo(percent: Int) {
        runCatching {
            val stream = AudioManager.STREAM_MUSIC
            val current = audioManager.getStreamVolume(stream)
            val original = originalVolume ?: current.also { originalVolume = it }
            val target = MusicVolumeCalculator.targetVolume(
                currentVolume = original,
                maxVolume = audioManager.getStreamMaxVolume(stream),
                duckPercent = percent,
            )
            duckedVolume = target
            saveState(original, target)
            if (current != target) audioManager.setStreamVolume(stream, target, 0)
            TrackTalkDebugLog.event(
                "MUSIC_ATTENUATION",
                "implementation" to "STREAM_MUSIC_SET_STREAM_VOLUME",
                "streamType" to "STREAM_MUSIC",
                "originalVolume" to original,
                "currentVolume" to current,
                "targetVolume" to target,
                "streamMaxVolume" to audioManager.getStreamMaxVolume(stream),
                "musicDuckPercent" to percent,
                "ttsUsage" to "USAGE_ASSISTANCE_ACCESSIBILITY",
            )
        }
    }

    @Synchronized
    fun restore() {
        val original = originalVolume ?: return
        val restored = runCatching {
            val stream = AudioManager.STREAM_MUSIC
            val current = audioManager.getStreamVolume(stream)
            // Respect a volume change made by the user while the announcement was playing.
            if (duckedVolume == null || current == duckedVolume) {
                audioManager.setStreamVolume(stream, original, 0)
            }
            TrackTalkDebugLog.event(
                "MUSIC_ATTENUATION_RESTORE",
                "streamType" to "STREAM_MUSIC",
                "originalVolume" to original,
                "duckedVolume" to duckedVolume,
                "currentVolume" to current,
            )
            true
        }.getOrDefault(false)
        if (!restored) return
        originalVolume = null
        duckedVolume = null
        clearSavedState()
    }

    private fun recoverInterruptedDuck() {
        val storedOriginal = preferences.getInt(KEY_ORIGINAL_VOLUME, NO_VOLUME)
        val storedDucked = preferences.getInt(KEY_DUCKED_VOLUME, NO_VOLUME)
        if (storedOriginal == NO_VOLUME) return

        val recovered = runCatching {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            // Only undo the previous duck if the user did not change the volume meanwhile.
            if (storedDucked == NO_VOLUME || current == storedDucked) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, storedOriginal, 0)
            }
            true
        }.getOrDefault(false)
        if (recovered) clearSavedState()
    }

    private fun saveState(original: Int, ducked: Int) {
        preferences.edit()
            .putInt(KEY_ORIGINAL_VOLUME, original)
            .putInt(KEY_DUCKED_VOLUME, ducked)
            .apply()
    }

    private fun clearSavedState() {
        preferences.edit()
            .remove(KEY_ORIGINAL_VOLUME)
            .remove(KEY_DUCKED_VOLUME)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "tracktalk_music_volume"
        const val KEY_ORIGINAL_VOLUME = "original_volume"
        const val KEY_DUCKED_VOLUME = "ducked_volume"
        const val NO_VOLUME = -1
    }
}
