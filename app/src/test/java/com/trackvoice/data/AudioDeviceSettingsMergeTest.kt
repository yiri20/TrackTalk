package com.trackvoice.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeviceSettingsMergeTest {
    @Test
    fun explicitNonDefaultLegacyChoicesWinConflicts() {
        val merged = mergeAudioDeviceSettings(
            canonicalKey = "bluetooth:canonical",
            displayName = "Space One Pro",
            candidates = listOf(
                AudioDeviceSettings("legacy-a2dp", "Space One Pro", autoEnable = true, enabled = true),
                AudioDeviceSettings("legacy-sco", "Space One Pro", autoEnable = false, enabled = false),
            ),
        )

        assertTrue(merged.autoEnable)
        assertFalse(merged.enabled)
    }

    @Test
    fun untouchedDeviceUsesExistingDefaults() {
        val merged = mergeAudioDeviceSettings(
            canonicalKey = "bluetooth:new",
            displayName = "New Headphones",
            candidates = emptyList(),
        )

        assertFalse(merged.autoEnable)
        assertTrue(merged.enabled)
    }
}
