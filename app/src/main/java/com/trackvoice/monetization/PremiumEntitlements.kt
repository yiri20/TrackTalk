package com.trackvoice.monetization

import com.trackvoice.data.UserSettings
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.DEFAULT_MUSIC_DUCK_PERCENT
import com.trackvoice.data.DEFAULT_TTS_VOLUME

fun UserSettings.forPremiumEntitlement(isPremium: Boolean): UserSettings {
    if (isPremium) return this
    return copy(
        autoEnableOnScreenOff = false,
        bluetoothOnlyForAutoEnable = false,
        musicTreatment = com.trackvoice.data.MusicTreatment.DUCK,
        musicDuckPercent = DEFAULT_MUSIC_DUCK_PERCENT,
        trackStartBehavior = com.trackvoice.data.TrackStartBehavior.PLAY_IMMEDIATELY,
        timing = com.trackvoice.data.AnnouncementTiming.IMMEDIATE,
        defaultMode = AnnouncementMode.SMART,
        // Field selection and ordering are part of the complete free core.
        // Only the old source-specific values are ignored by the runtime.
        useContentTypeSettings = false,
        announcementOrder = AnnouncementOrder.DEFAULT,
        albumMode = AnnouncementMode.ALBUM,
        playlistMode = AnnouncementMode.PLAYLIST,
        allowRepeatAnnouncements = false,
        minimumPlaybackSeconds = 0,
        speechRate = 1f,
        pitch = 1f,
        volume = DEFAULT_TTS_VOLUME,
        algorithmMode = AnnouncementMode.TITLE_AND_ARTIST,
        albumNameFirstTrackOnly = false,
        raiseDeviceVolume = false,
        deviceVolumePercent = 90,
    )
}
