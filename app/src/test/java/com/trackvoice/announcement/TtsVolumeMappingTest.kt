package com.trackvoice.announcement

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsVolumeMappingTest {
    @Test
    fun uiVolumeMapsDirectlyToTtsGain() {
        assertEquals(0f, TtsVolumeMapping.parameterForUiVolume(0f), 0f)
        assertEquals(0.25f, TtsVolumeMapping.parameterForUiVolume(0.25f), 0f)
        assertEquals(0.50f, TtsVolumeMapping.parameterForUiVolume(0.50f), 0f)
        assertEquals(0.85f, TtsVolumeMapping.parameterForUiVolume(0.85f), 0f)
        assertEquals(1f, TtsVolumeMapping.parameterForUiVolume(1f), 0f)
    }

    @Test
    fun musicDuckDoesNotMultiplyTtsGain() {
        assertEquals(0.85f, TtsVolumeMapping.parameterForUiVolume(0.85f), 0f)
    }
}
