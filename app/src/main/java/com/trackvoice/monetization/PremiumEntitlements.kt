package com.trackvoice.monetization

import com.trackvoice.data.UserSettings
import com.trackvoice.data.AppSettings
import com.trackvoice.data.AnnouncementMode
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
        albumMode = AnnouncementMode.ALBUM,
        playlistMode = AnnouncementMode.PLAYLIST,
        allowRepeatAnnouncements = false,
        minimumPlaybackSeconds = 0,
        speechRate = 1f,
        pitch = 1f,
        volume = DEFAULT_TTS_VOLUME,
        raiseDeviceVolume = false,
        deviceVolumePercent = 90,
    )
}

fun AppSettings.forPremiumEntitlement(isPremium: Boolean): AppSettings {
    if (isPremium) return this
    return copy(
        useCustomGuideSettings = false,
        mode = AnnouncementMode.SMART,
        readTitle = true,
        readArtist = true,
        readTrackNumber = true,
        readAlbum = true,
        readCollection = true,
        timing = null,
        alwaysExclude = false,
    )
}
