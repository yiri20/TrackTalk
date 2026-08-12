package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.AppSettings
import com.trackvoice.data.UserSettings
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackCollectionResolver

enum class AnnouncementSkipReason {
    DISABLED,
    APP_DISABLED,
    APP_EXCLUDED,
    NO_TITLE,
    SPEAKER_OUTPUT,
    TOO_EARLY,
    NO_TEXT,
}

data class AnnouncementDecision(
    val shouldAnnounce: Boolean,
    val text: String? = null,
    val mode: AnnouncementMode,
    val delayMs: Long,
    val skipReason: AnnouncementSkipReason? = null,
)

object AnnouncementPolicy {
    fun decide(
        event: PlaybackEvent,
        userSettings: UserSettings,
        appSettings: AppSettings?,
        effectiveEnabled: Boolean = userSettings.enabled,
        externalAudioOutput: Boolean = true,
    ): AnnouncementDecision {
        val appGuideSettings = appSettings?.takeIf { it.useCustomGuideSettings }
        val collection = PlaybackCollectionResolver.resolve(event)
        val mode = resolveMode(collection, userSettings, appGuideSettings)
        val configuredDelayMs = if (userSettings.trackStartBehavior == com.trackvoice.data.TrackStartBehavior.ANNOUNCE_THEN_PLAY) {
            0L
        } else (appGuideSettings?.timing ?: userSettings.timing).let {
            when (it) {
                com.trackvoice.data.AnnouncementTiming.IMMEDIATE -> 0L
                com.trackvoice.data.AnnouncementTiming.DELAYED,
                com.trackvoice.data.AnnouncementTiming.BETWEEN_TRACKS,
                -> userSettings.delaySeconds.coerceIn(0, 2) * 1_000L
            }
        }

        val minimumPosition = userSettings.minimumPlaybackSeconds.coerceIn(0, 120) * 1_000L
        val minimumRemainingMs = (minimumPosition - (event.playbackPosition ?: 0L)).coerceAtLeast(0L)
        val delayMs = maxOf(configuredDelayMs, minimumRemainingMs)

        if (!effectiveEnabled) return skipped(mode, delayMs, AnnouncementSkipReason.DISABLED)
        if (appSettings?.enabled == false) return skipped(mode, delayMs, AnnouncementSkipReason.APP_DISABLED)
        if (appSettings?.alwaysExclude == true) return skipped(mode, delayMs, AnnouncementSkipReason.APP_EXCLUDED)
        if (!event.hasTitle) return skipped(mode, delayMs, AnnouncementSkipReason.NO_TITLE)
        if (userSettings.headphonesOnly && !externalAudioOutput) {
            return skipped(mode, delayMs, AnnouncementSkipReason.SPEAKER_OUTPUT)
        }
        if (userSettings.suppressDuringSpeakerPlayback && !externalAudioOutput) {
            return skipped(mode, delayMs, AnnouncementSkipReason.SPEAKER_OUTPUT)
        }

        val formatOptions = if (appGuideSettings != null) {
            AnnouncementFormatOptions(
                readTitle = appGuideSettings.readTitle,
                readArtist = appGuideSettings.readArtist,
                readTrackNumber = appGuideSettings.readTrackNumber,
                readAlbum = appGuideSettings.readAlbum,
                readCollection = appGuideSettings.readCollection,
            )
        } else {
            when (collection) {
                PlaybackCollection.ALBUM -> userSettings.albumReadFields.toFormatOptions()
                PlaybackCollection.PLAYLIST -> userSettings.playlistReadFields.toFormatOptions()
                PlaybackCollection.ALGORITHMIC -> userSettings.algorithmReadFields.toFormatOptions()
                PlaybackCollection.UNKNOWN -> AnnouncementFormatOptions()
            }
        }

        val text = AnnouncementFormatter.format(
            event = event,
            mode = mode,
            options = formatOptions,
            collection = collection,
        )
        return if (text == null) {
            skipped(mode, delayMs, AnnouncementSkipReason.NO_TEXT)
        } else {
            AnnouncementDecision(true, text, mode, delayMs)
        }
    }

    private fun skipped(
        mode: AnnouncementMode,
        delayMs: Long,
        reason: AnnouncementSkipReason,
    ) = AnnouncementDecision(
        shouldAnnounce = false,
        mode = mode,
        delayMs = delayMs,
        skipReason = reason,
    )

    private fun resolveMode(
        collection: PlaybackCollection,
        userSettings: UserSettings,
        appSettings: AppSettings?,
    ): AnnouncementMode {
        if (appSettings != null) {
            return when (collection) {
                PlaybackCollection.ALBUM -> if (appSettings.readAlbum || appSettings.readTrackNumber) {
                    AnnouncementMode.ALBUM
                } else {
                    AnnouncementMode.TITLE_AND_ARTIST
                }
                PlaybackCollection.PLAYLIST -> if (appSettings.readCollection) {
                    AnnouncementMode.PLAYLIST
                } else {
                    AnnouncementMode.TITLE_AND_ARTIST
                }
                PlaybackCollection.ALGORITHMIC,
                PlaybackCollection.UNKNOWN,
                -> AnnouncementMode.TITLE_AND_ARTIST
            }
        }

        if (userSettings.defaultMode != AnnouncementMode.SMART) return userSettings.defaultMode
        return when (collection) {
            PlaybackCollection.ALBUM -> userSettings.albumMode
            PlaybackCollection.PLAYLIST -> userSettings.playlistMode
            PlaybackCollection.ALGORITHMIC -> userSettings.algorithmMode
            PlaybackCollection.UNKNOWN -> AnnouncementMode.TITLE_AND_ARTIST
        }
    }
}

private fun Set<AnnouncementReadField>.toFormatOptions() = AnnouncementFormatOptions(
    readTitle = AnnouncementReadField.TITLE in this,
    readArtist = AnnouncementReadField.ARTIST in this,
    readTrackNumber = AnnouncementReadField.TRACK_NUMBER in this,
    readAlbum = AnnouncementReadField.ALBUM in this,
    readCollection = AnnouncementReadField.COLLECTION in this,
)
