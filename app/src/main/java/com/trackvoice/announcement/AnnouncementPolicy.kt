package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
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
        val configuredMode = appSettings?.mode ?: userSettings.defaultMode
        val mode = if (configuredMode == AnnouncementMode.SMART) {
            when (PlaybackCollectionResolver.resolve(event)) {
                PlaybackCollection.ALBUM -> userSettings.albumMode
                PlaybackCollection.PLAYLIST -> userSettings.playlistMode
                PlaybackCollection.UNKNOWN -> AnnouncementMode.TITLE_AND_ARTIST
            }
        } else {
            configuredMode
        }
        val configuredDelayMs = if (userSettings.trackStartBehavior == com.trackvoice.data.TrackStartBehavior.ANNOUNCE_THEN_PLAY) {
            0L
        } else (appSettings?.timing ?: userSettings.timing).let {
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

        val text = AnnouncementFormatter.format(
            event = event,
            mode = mode,
            options = AnnouncementFormatOptions(
                readTitle = appSettings?.readTitle ?: true,
                readArtist = appSettings?.readArtist ?: true,
                readTrackNumber = appSettings?.readTrackNumber ?: true,
                readAlbum = appSettings?.readAlbum ?: true,
                readCollection = appSettings?.readCollection ?: true,
            ),
            collection = PlaybackCollectionResolver.resolve(event),
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
}
