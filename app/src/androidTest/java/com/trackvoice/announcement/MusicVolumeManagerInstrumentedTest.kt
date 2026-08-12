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
class MusicVolumeManagerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var originalVolume = 0

    @Before
    fun setUp() {
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    }

    @After
    fun tearDown() {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        context.getSharedPreferences("tracktalk_music_volume", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun ducksAndRestoresMediaVolumeIncludingInterruptedProcessRecovery() {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val manager = MusicVolumeManager(context)
        manager.duckTo(50)

        assertEquals(
            MusicVolumeCalculator.targetVolume(originalVolume, maxVolume, 50),
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        )

        // A new manager represents a fresh process after an interrupted announcement.
        MusicVolumeManager(context)
        assertEquals(originalVolume, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))

        manager.restore()
    }
}
