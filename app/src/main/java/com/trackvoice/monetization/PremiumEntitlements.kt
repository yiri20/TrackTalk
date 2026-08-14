package com.trackvoice.monetization

import com.trackvoice.data.UserSettings
import com.trackvoice.data.AppSettings
import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.CollectionFallback
import com.trackvoice.data.DEFAULT_ALBUM_READ_FIELDS
import com.trackvoice.data.DEFAULT_ALGORITHMIC_READ_FIELDS
import com.trackvoice.data.DEFAULT_GLOBAL_READ_FIELDS
import com.trackvoice.data.DEFAULT_PLAYLIST_READ_FIELDS
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
        useContentTypeSettings = true,
        defaultReadFields = DEFAULT_GLOBAL_READ_FIELDS,
        announcementOrder = AnnouncementOrder.DEFAULT,
        albumMode = AnnouncementMode.ALBUM,
        playlistMode = AnnouncementMode.PLAYLIST,
        allowRepeatAnnouncements = false,
        minimumPlaybackSeconds = 0,
        speechRate = 1f,
        pitch = 1f,
        volume = DEFAULT_TTS_VOLUME,
        algorithmMode = AnnouncementMode.TITLE_AND_ARTIST,
        albumReadFields = DEFAULT_ALBUM_READ_FIELDS,
        albumNameFirstTrackOnly = false,
        playlistReadFields = DEFAULT_PLAYLIST_READ_FIELDS,
        algorithmReadFields = DEFAULT_ALGORITHMIC_READ_FIELDS,
        raiseDeviceVolume = false,
        deviceVolumePercent = 90,
    )
}

fun AppSettings.forPremiumEntitlement(isPremium: Boolean): AppSettings {
    if (isPremium) return this
    return copy(
        useCustomGuideSettings = false,
        mode = AnnouncementMode.SMART,
        collectionFallback = CollectionFallback.AUTO,
        readTitle = true,
        readArtist = true,
        readTrackNumber = true,
        readAlbum = true,
        readCollection = true,
        timing = null,
        alwaysExclude = false,
    )
}
