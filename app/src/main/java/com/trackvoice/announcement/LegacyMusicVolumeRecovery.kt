package com.trackvoice.announcement

import android.content.Context
import android.media.AudioManager
import androidx.core.content.edit
import com.trackvoice.diagnostics.TrackTalkDebugLog

/**
 * One-time compatibility recovery for builds that directly changed STREAM_MUSIC
 * while an announcement was playing. Normal announcements never use this path.
 */
class LegacyMusicVolumeRecovery(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    init {
        recover()
    }

    private fun recover() {
        val storedOriginal = preferences.getInt(KEY_ORIGINAL_VOLUME, NO_VOLUME)
        val storedDucked = preferences.getInt(KEY_DUCKED_VOLUME, NO_VOLUME)
        if (storedOriginal == NO_VOLUME) return

        val recovered = runCatching {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val userChangedVolume = storedDucked != NO_VOLUME && current != storedDucked
            if (!userChangedVolume) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, storedOriginal, 0)
            }
            TrackTalkDebugLog.event(
                "LEGACY_MUSIC_ATTENUATION_RECOVERY",
                "restored" to !userChangedVolume,
                "userChangedVolume" to userChangedVolume,
                "originalVolume" to storedOriginal,
                "previousDuckedVolume" to storedDucked,
                "currentVolume" to current,
            )
            true
        }.getOrDefault(false)
        if (recovered) clearSavedState()
    }

    private fun clearSavedState() {
        preferences.edit {
            remove(KEY_ORIGINAL_VOLUME)
            remove(KEY_DUCKED_VOLUME)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "tracktalk_music_volume"
        const val KEY_ORIGINAL_VOLUME = "original_volume"
        const val KEY_DUCKED_VOLUME = "ducked_volume"
        const val NO_VOLUME = -1
    }
}
