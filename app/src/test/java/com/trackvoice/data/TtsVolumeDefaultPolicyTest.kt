package com.trackvoice.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVolumeDefaultPolicyTest {
    @Test
    fun brandNewConfigurationUsesEightyPercent() {
        assertEquals(80, DEFAULT_TTS_VOLUME_PERCENT)
        assertEquals(0.80f, TtsVolumeDefaultPolicy.valueFor(null), 0f)
        assertTrue(TtsVolumeDefaultPolicy.shouldWriteDefault(null))
    }

    @Test
    fun everyStoredVolumeIsPreservedDuringDefaultMigration() {
        listOf(0f, 0.4f, 0.8f, 0.85f, 1f).forEach { storedVolume ->
            assertFalse(TtsVolumeDefaultPolicy.shouldWriteDefault(storedVolume))
            assertEquals(storedVolume, TtsVolumeDefaultPolicy.valueFor(storedVolume), 0f)
        }
    }
}
