package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
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
    val formatOptions: AnnouncementFormatOptions = AnnouncementFormatOptions(),
    val collection: PlaybackCollection = PlaybackCollection.UNKNOWN,
    val skipReason: AnnouncementSkipReason? = null,
)

object AnnouncementPolicy {
    fun decide(
        event: PlaybackEvent,
        userSettings: UserSettings,
        appSettings: AppSettings?,
        effectiveEnabled: Boolean = userSettings.enabled,
        externalAudioOutput: Boolean = true,
        collectionOverride: PlaybackCollection? = null,
    ): AnnouncementDecision {
        val appGuideSettings = appSettings?.takeIf { it.useCustomGuideSettings }
        val detectedCollection = collectionOverride ?: PlaybackCollectionResolver.resolve(event)
        val collection = PlaybackCollectionResolver.applyFallback(
            detected = detectedCollection,
            fallback = appGuideSettings?.collectionFallback
                ?: com.trackvoice.data.CollectionFallback.AUTO,
        )
        val typeSpecificActive = userSettings.useContentTypeSettings && collection != PlaybackCollection.UNKNOWN
        val mode = resolveMode(collection, userSettings, appGuideSettings)
        val timing = appGuideSettings?.timing ?: userSettings.timing
        val announceBeforePlayback = userSettings.trackStartBehavior == com.trackvoice.data.TrackStartBehavior.ANNOUNCE_THEN_PLAY
        val configuredDelayMs = if (announceBeforePlayback) {
            0L
        } else timing.let {
            when (it) {
                com.trackvoice.data.AnnouncementTiming.IMMEDIATE -> 0L
                com.trackvoice.data.AnnouncementTiming.DELAYED,
                com.trackvoice.data.AnnouncementTiming.BETWEEN_TRACKS,
                -> userSettings.delaySeconds.coerceIn(0, 2) * 1_000L
            }
        }

        // Immediate reading (and announce-before-playback) has no meaningful
        // elapsed-playback threshold. Do not let a stale minimum value delay
        // an announcement after the user selects "Read now".
        val minimumPosition = if (timing == com.trackvoice.data.AnnouncementTiming.IMMEDIATE || announceBeforePlayback) {
            0L
        } else {
            userSettings.minimumPlaybackSeconds.coerceIn(0, 120) * 1_000L
        }
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

        val formatOptions = if (typeSpecificActive) {
            userSettings.readFieldsFor(collection).toFormatOptions(
                albumNameFirstTrackOnly = userSettings.albumNameFirstTrackOnly,
                announcementOrder = userSettings.announcementOrder,
            )
        } else if (appGuideSettings != null) {
            AnnouncementFormatOptions(
                readTitle = appGuideSettings.readTitle,
                readArtist = appGuideSettings.readArtist,
                readTrackNumber = appGuideSettings.readTrackNumber,
                readAlbum = appGuideSettings.readAlbum,
                readCollection = appGuideSettings.readCollection,
                albumNameFirstTrackOnly = userSettings.albumNameFirstTrackOnly,
                announcementOrder = userSettings.announcementOrder,
            )
        } else {
            userSettings.readFieldsFor(collection).toFormatOptions(
                albumNameFirstTrackOnly = userSettings.albumNameFirstTrackOnly,
                announcementOrder = userSettings.announcementOrder,
            )
        }

        val text = AnnouncementFormatter.format(
            event = event,
            mode = mode,
            options = formatOptions,
            collection = collection,
            voiceLanguage = userSettings.voiceLanguage,
        )
        return if (text == null) {
            skipped(mode, delayMs, AnnouncementSkipReason.NO_TEXT)
        } else {
            AnnouncementDecision(
                shouldAnnounce = true,
                text = text,
                mode = mode,
                delayMs = delayMs,
                formatOptions = formatOptions,
                collection = collection,
            )
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

    fun resolveMode(
        collection: PlaybackCollection,
        userSettings: UserSettings,
        appSettings: AppSettings?,
    ): AnnouncementMode {
        val typeSpecificActive = userSettings.useContentTypeSettings && collection != PlaybackCollection.UNKNOWN
        // Once type-specific settings are enabled, a detected content type is
        // the source of truth. This prevents a stale global format or an app
        // checklist from silently discarding checked album/track fields.
        if (typeSpecificActive) {
            return when (collection) {
                PlaybackCollection.ALBUM -> userSettings.albumReadFields.toAlbumMode()
                PlaybackCollection.PLAYLIST -> userSettings.playlistReadFields.toPlaylistMode()
                PlaybackCollection.ALGORITHMIC -> userSettings.algorithmReadFields.toConfiguredMode(userSettings.algorithmMode)
                PlaybackCollection.UNKNOWN -> error("unreachable")
            }
        }

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
                -> AnnouncementMode.TITLE_AND_ARTIST
                PlaybackCollection.UNKNOWN -> if (appSettings.readAlbum || appSettings.readTrackNumber) {
                    // A directly selected song can still expose its album
                    // and track number. Keep those fields readable without
                    // claiming that the surrounding queue is an album.
                    AnnouncementMode.ALBUM
                } else {
                    AnnouncementMode.TITLE_AND_ARTIST
                }
            }
        }

        if (!userSettings.useContentTypeSettings) {
            return if (userSettings.defaultMode == AnnouncementMode.SMART) {
                userSettings.defaultReadFields.toAnnouncementMode()
            } else {
                userSettings.defaultMode
            }
        }

        if (userSettings.defaultMode != AnnouncementMode.SMART) return userSettings.defaultMode

        return when (collection) {
            PlaybackCollection.UNKNOWN -> userSettings.defaultReadFields.toAnnouncementMode()
            PlaybackCollection.ALBUM,
            PlaybackCollection.PLAYLIST,
            PlaybackCollection.ALGORITHMIC,
            -> userSettings.defaultReadFields.toAnnouncementMode()
        }
    }

    private fun UserSettings.readFieldsFor(collection: PlaybackCollection): Set<AnnouncementReadField> =
        if (!useContentTypeSettings) {
            defaultReadFields
        } else {
            when (collection) {
                PlaybackCollection.ALBUM -> albumReadFields
                PlaybackCollection.PLAYLIST -> playlistReadFields
                PlaybackCollection.ALGORITHMIC -> algorithmReadFields
                PlaybackCollection.UNKNOWN -> defaultReadFields
            }
        }
}

private fun Set<AnnouncementReadField>.toFormatOptions(
    albumNameFirstTrackOnly: Boolean = false,
    announcementOrder: AnnouncementOrder = AnnouncementOrder.DEFAULT,
) = AnnouncementFormatOptions(
    readTitle = AnnouncementReadField.TITLE in this,
    readArtist = AnnouncementReadField.ARTIST in this,
    readTrackNumber = AnnouncementReadField.TRACK_NUMBER in this,
    readAlbum = AnnouncementReadField.ALBUM in this,
    readCollection = AnnouncementReadField.COLLECTION in this,
    albumNameFirstTrackOnly = albumNameFirstTrackOnly,
    announcementOrder = announcementOrder,
)

private fun Set<AnnouncementReadField>.toAnnouncementMode(): AnnouncementMode = when {
    AnnouncementReadField.ALBUM in this || AnnouncementReadField.TRACK_NUMBER in this -> AnnouncementMode.ALBUM
    AnnouncementReadField.COLLECTION in this -> AnnouncementMode.PLAYLIST
    AnnouncementReadField.TITLE in this && AnnouncementReadField.ARTIST in this -> AnnouncementMode.TITLE_AND_ARTIST
    AnnouncementReadField.TITLE in this -> AnnouncementMode.TITLE_ONLY
    else -> AnnouncementMode.TITLE_AND_ARTIST
}

private fun Set<AnnouncementReadField>.toConfiguredMode(configuredMode: AnnouncementMode): AnnouncementMode {
    // The checklist is the source of truth for metadata fields. Older saved
    // settings may still contain TITLE_AND_ARTIST in the legacy mode field;
    // do not let that stale value hide a newly checked album or track number.
    return when {
        AnnouncementReadField.ALBUM in this || AnnouncementReadField.TRACK_NUMBER in this -> AnnouncementMode.ALBUM
        AnnouncementReadField.COLLECTION in this -> AnnouncementMode.PLAYLIST
        configuredMode != AnnouncementMode.SMART -> configuredMode
        else -> toAnnouncementMode()
    }
}

private fun Set<AnnouncementReadField>.toAlbumMode(): AnnouncementMode = when {
    AnnouncementReadField.ALBUM in this || AnnouncementReadField.TRACK_NUMBER in this -> AnnouncementMode.ALBUM
    AnnouncementReadField.TITLE in this && AnnouncementReadField.ARTIST in this -> AnnouncementMode.TITLE_AND_ARTIST
    AnnouncementReadField.TITLE in this -> AnnouncementMode.TITLE_ONLY
    else -> AnnouncementMode.TITLE_AND_ARTIST
}

private fun Set<AnnouncementReadField>.toPlaylistMode(): AnnouncementMode = when {
    AnnouncementReadField.COLLECTION in this ||
        AnnouncementReadField.ALBUM in this ||
        AnnouncementReadField.TRACK_NUMBER in this -> AnnouncementMode.PLAYLIST
    AnnouncementReadField.TITLE in this && AnnouncementReadField.ARTIST in this -> AnnouncementMode.TITLE_AND_ARTIST
    AnnouncementReadField.TITLE in this -> AnnouncementMode.TITLE_ONLY
    else -> AnnouncementMode.TITLE_AND_ARTIST
}
