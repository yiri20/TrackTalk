package com.trackvoice.announcement

import android.content.Context
import android.media.AudioManager
import com.trackvoice.data.MAX_MUSIC_DUCK_PERCENT
import com.trackvoice.data.MIN_MUSIC_DUCK_PERCENT
import kotlin.math.roundToInt

object MusicVolumeCalculator {
    fun targetVolume(currentVolume: Int, maxVolume: Int, duckPercent: Int): Int {
        if (maxVolume <= 0) return 0
        return (currentVolume * duckPercent.coerceIn(MIN_MUSIC_DUCK_PERCENT, MAX_MUSIC_DUCK_PERCENT) / 100f)
            .roundToInt()
            .coerceIn(0, maxVolume)
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
        }
    }

    fun restore() {
        val original = originalVolume ?: return
        runCatching {
            val stream = AudioManager.STREAM_MUSIC
            val current = audioManager.getStreamVolume(stream)
            // Respect a volume change made by the user while the announcement was playing.
            if (duckedVolume == null || current == duckedVolume) {
                audioManager.setStreamVolume(stream, original, 0)
            }
        }
        originalVolume = null
        duckedVolume = null
        clearSavedState()
    }

    private fun recoverInterruptedDuck() {
        val storedOriginal = preferences.getInt(KEY_ORIGINAL_VOLUME, NO_VOLUME)
        val storedDucked = preferences.getInt(KEY_DUCKED_VOLUME, NO_VOLUME)
        if (storedOriginal == NO_VOLUME) return

        runCatching {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            // Only undo the previous duck if the user did not change the volume meanwhile.
            if (storedDucked == NO_VOLUME || current == storedDucked) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, storedOriginal, 0)
            }
        }
        clearSavedState()
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
