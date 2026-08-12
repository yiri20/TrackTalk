package com.trackvoice.announcement

import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.TrackFingerprint

class DuplicateSuppressor(
    private val repeatCooldownMs: Long = 30_000L,
) {
    private val announcedTracks = linkedMapOf<String, AnnouncedTrack>()

    fun shouldAnnounce(
        event: PlaybackEvent,
        allowRepeat: Boolean,
        now: Long,
        announcementText: String? = null,
    ): Boolean {
        if (!event.hasTitle && event.mediaId.isNullOrBlank()) return false
        val base = TrackFingerprint.announcementBase(event)
        val previous = announcedTracks.values
            .asSequence()
            .filter { it.base == base }
            .filter { it.isCompatibleWith(event) }
            .maxByOrNull(AnnouncedTrack::announcedAt)
        return when {
            previous == null -> true
            previous.isMetadataEnrichedBy(event) &&
                announcementText != null &&
                previous.announcementText != null &&
                previous.announcementText != announcementText -> true
            !allowRepeat -> false
            now - previous.announcedAt >= repeatCooldownMs -> true
            else -> false
        }
    }

    fun markAnnounced(event: PlaybackEvent, now: Long, announcementText: String? = null) {
        val fingerprint = TrackFingerprint.announcement(event)
        announcedTracks[fingerprint] = AnnouncedTrack(
            base = TrackFingerprint.announcementBase(event),
            trackNumber = event.trackNumber,
            discNumber = event.discNumber,
            artistPresent = !event.artist.isNullOrBlank(),
            albumPresent = !event.album.isNullOrBlank(),
            announcementText = announcementText,
            announcedAt = now,
        )
        while (announcedTracks.size > 64) {
            announcedTracks.remove(announcedTracks.keys.first())
        }
    }

    fun clear() = announcedTracks.clear()

    private data class AnnouncedTrack(
        val base: String,
        val trackNumber: Int?,
        val discNumber: Int?,
        val artistPresent: Boolean,
        val albumPresent: Boolean,
        val announcementText: String?,
        val announcedAt: Long,
    ) {
        /**
         * A later callback that only fills a missing optional field is still
         * the same track. Two explicit, different track/disc values indicate
         * a real queue item change and must remain announceable.
         */
        fun isCompatibleWith(event: PlaybackEvent): Boolean =
            (trackNumber == null || event.trackNumber == null || trackNumber == event.trackNumber) &&
                (discNumber == null || event.discNumber == null || discNumber == event.discNumber)

        fun isMetadataEnrichedBy(event: PlaybackEvent): Boolean =
            (!artistPresent && !event.artist.isNullOrBlank()) ||
                (!albumPresent && !event.album.isNullOrBlank()) ||
                (trackNumber == null && event.trackNumberReliable && event.trackNumber != null)
    }
}
