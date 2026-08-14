package com.trackvoice.monetization

import com.trackvoice.data.UserSettings
import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppSettings
import com.trackvoice.data.DEFAULT_TTS_VOLUME
import com.trackvoice.data.DEFAULT_TTS_VOLUME_PERCENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumEntitlementsTest {
    @Test
    fun freeVoiceDefaultIsReducedToComfortableLevel() {
        assertEquals(40, DEFAULT_TTS_VOLUME_PERCENT)
        assertEquals(0.40f, DEFAULT_TTS_VOLUME, 0f)
    }

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
        assertEquals(DEFAULT_TTS_VOLUME, effective.volume)
        assertEquals(MusicTreatment.DUCK, effective.musicTreatment)
        assertEquals(TrackStartBehavior.PLAY_IMMEDIATELY, effective.trackStartBehavior)
        assertEquals(AnnouncementMode.TITLE_AND_ARTIST, effective.algorithmMode)
        assertEquals(AnnouncementOrder.DEFAULT, effective.announcementOrder)
        assertFalse(effective.albumNameFirstTrackOnly)
        assertFalse(effective.raiseDeviceVolume)
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

    @Test
    fun freeAppSettingsReturnToBasicAnnouncementDefaults() {
        val effective = AppSettings(
            packageName = "com.example.player",
            appName = "Player",
            useCustomGuideSettings = true,
            mode = AnnouncementMode.TITLE_ONLY,
            readArtist = false,
            readAlbum = false,
            readCollection = false,
            timing = AnnouncementTiming.DELAYED,
        ).forPremiumEntitlement(isPremium = false)

        assertEquals(AnnouncementMode.SMART, effective.mode)
        assertTrue(effective.readArtist)
        assertTrue(effective.readAlbum)
        assertTrue(effective.readCollection)
        assertEquals(null, effective.timing)
        assertFalse(effective.useCustomGuideSettings)
    }
}
