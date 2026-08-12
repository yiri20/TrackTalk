package com.trackvoice.monetization

import com.trackvoice.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumEntitlementsTest {
    @Test
    fun freeUserGetsBasicVoiceAndAutomationDefaults() {
        val effective = UserSettings(
            autoEnableOnScreenOff = true,
            speechRate = 1.5f,
            pitch = 0.7f,
            volume = 0.4f,
            raiseDeviceVolume = false,
            deviceVolumePercent = 55,
        ).forPremiumEntitlement(isPremium = false)

        assertFalse(effective.autoEnableOnScreenOff)
        assertEquals(1f, effective.speechRate)
        assertEquals(1f, effective.pitch)
        assertEquals(1f, effective.volume)
        assertTrue(effective.raiseDeviceVolume)
        assertEquals(90, effective.deviceVolumePercent)
    }

    @Test
    fun premiumUserKeepsAdvancedSettings() {
        val settings = UserSettings(
            autoEnableOnScreenOff = true,
            speechRate = 1.5f,
            pitch = 0.7f,
            volume = 0.4f,
            raiseDeviceVolume = false,
            deviceVolumePercent = 55,
        )

        assertEquals(settings, settings.forPremiumEntitlement(isPremium = true))
    }
}
