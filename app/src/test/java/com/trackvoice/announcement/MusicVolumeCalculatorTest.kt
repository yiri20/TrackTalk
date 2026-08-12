package com.trackvoice.announcement

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicVolumeCalculatorTest {
    @Test
    fun duckPercentCalculatesFromOriginalMediaVolume() {
        assertEquals(7, MusicVolumeCalculator.targetVolume(currentVolume = 20, maxVolume = 25, duckPercent = 35))
    }

    @Test
    fun duckPercentIsClampedToSafeRange() {
        assertEquals(2, MusicVolumeCalculator.targetVolume(currentVolume = 20, maxVolume = 25, duckPercent = 0))
        assertEquals(16, MusicVolumeCalculator.targetVolume(currentVolume = 20, maxVolume = 25, duckPercent = 100))
    }
}
