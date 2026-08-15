package com.trackvoice.announcement

import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyMusicVolumeRecoveryInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val preferences = context.getSharedPreferences("tracktalk_music_volume", 0)
    private var originalVolume = 0

    @Before
    fun setUp() {
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        preferences.edit().clear().commit()
    }

    @Test
    fun recoversOnlyAStaleVolumeWrittenByAnOlderBuild() {
        val legacyDuckedVolume = (originalVolume - 1).coerceAtLeast(0)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, legacyDuckedVolume, 0)
        preferences.edit()
            .putInt("original_volume", originalVolume)
            .putInt("ducked_volume", legacyDuckedVolume)
            .commit()

        LegacyMusicVolumeRecovery(context)

        assertEquals(originalVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }
}
