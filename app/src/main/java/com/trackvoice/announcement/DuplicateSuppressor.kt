package com.trackvoice.announcement

import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackCollectionResolver
import com.trackvoice.media.TrackFingerprint
import java.util.Locale

/**
 * Matches media-session snapshots that describe one track. Media apps often
 * replace a temporary queue ID, fill in the artist later, or correct a title
 * after playback has already started. Those changes must not create a second
 * announcement.
 */
object AnnouncementTrackMatcher {
    fun matches(
        expected: PlaybackEvent,
        current: PlaybackEvent,
        requireSameSource: Boolean = true,
    ): Boolean {
        if (requireSameSource && expected.sourcePackageName != current.sourcePackageName) return false

        val expectedMediaId = expected.mediaId.normalizedOrNull()
        val currentMediaId = current.mediaId.normalizedOrNull()
        if (expectedMediaId != null && currentMediaId != null && expectedMediaId == currentMediaId) {
            return true
        }

        if (!compatibleText(expected.artist, current.artist)) return false
        val titleMatches = sameText(expected.title, current.title)
        val hasTemporaryTitle = isTemporaryTitle(expected.title) || isTemporaryTitle(current.title)
        val sameAlbum = compatibleText(expected.album, current.album) &&
            !expected.album.isNullOrBlank() &&
            !current.album.isNullOrBlank()
        val sameTrackNumber = expected.trackNumber != null &&
            current.trackNumber != null &&
            expected.trackNumber == current.trackNumber
        val sameDiscNumber = expected.discNumber == null ||
            current.discNumber == null ||
            expected.discNumber == current.discNumber
        val reliableTrackNumber = expected.trackNumberReliable && current.trackNumberReliable
        val trackNumbersConflict = expected.trackNumber != null &&
            current.trackNumber != null &&
            expected.trackNumber != current.trackNumber
        val discNumbersConflict = expected.discNumber != null &&
            current.discNumber != null &&
            expected.discNumber != current.discNumber

        if (titleMatches) {
            // When both snapshots have no media ID, explicit track/disc
            // changes are the only reliable sign of a different track.
            // With a media ID, allow one side to correct a missing number,
            // but do not merge two fully specified, conflicting tracks.
            if (expectedMediaId == null && currentMediaId == null) {
                return !trackNumbersConflict && !discNumbersConflict
            }
            // A changed provider ID plus a corrected track/disc number is a
            // common metadata hand-off (queue item -> canonical metadata),
            // not proof that the song changed. Title and artist are the
            // stronger identity during that hand-off.
            return true
        }

        // A title can be corrected from a queue description to canonical
        // metadata. Album + reliable track/disc is a safe alias for that
        // short-lived transition, even when the provider replaces its media
        // ID at the same time. This also covers the common case where the
        // first callback contains a queue title and the next one contains the
        // actual title.
        return (sameAlbum && sameTrackNumber && sameDiscNumber && reliableTrackNumber) ||
            sameQueueItem(expected, current)
    }

    fun sameContent(
        expected: PlaybackEvent,
        current: PlaybackEvent,
        requireSameSource: Boolean = true,
    ): Boolean {
        if (requireSameSource && expected.sourcePackageName != current.sourcePackageName) return false
        return sameText(expected.title, current.title) && compatibleText(expected.artist, current.artist)
    }

    /**
     * Conservative identity used after speech has already been committed.
     * Album/track aliases are useful while a pending callback is settling,
     * but they are too broad for suppressing a completed announcement because
     * different songs can share a track number across albums/providers.
     */
    fun matchesForDuplicateSuppression(
        expected: PlaybackEvent,
        current: PlaybackEvent,
        requireSameSource: Boolean = true,
    ): Boolean {
        if (requireSameSource && expected.sourcePackageName != current.sourcePackageName) return false
        val expectedMediaId = expected.mediaId.normalizedOrNull()
        val currentMediaId = current.mediaId.normalizedOrNull()
        if (expectedMediaId != null && currentMediaId != null && expectedMediaId == currentMediaId) return true
        if (!compatibleText(expected.artist, current.artist)) return false

        val titleMatches = sameText(expected.title, current.title)
        val hasTemporaryTitle = isTemporaryTitle(expected.title) || isTemporaryTitle(current.title)
        val sameAlbum = compatibleText(expected.album, current.album) &&
            !expected.album.isNullOrBlank() &&
            !current.album.isNullOrBlank()
        val sameTrackNumber = expected.trackNumber != null &&
            current.trackNumber != null &&
            expected.trackNumber == current.trackNumber
        val sameDiscNumber = expected.discNumber == null ||
            current.discNumber == null ||
            expected.discNumber == current.discNumber
        val reliableTrackNumber = expected.trackNumberReliable && current.trackNumberReliable
        val validTrackNumbersConflict = expected.trackNumber
            ?.takeIf { expected.trackNumberReliable && it in 1..999 }
            ?.let { expectedTrack ->
                current.trackNumber
                    ?.takeIf { current.trackNumberReliable && it in 1..999 }
                    ?.let { currentTrack -> expectedTrack != currentTrack }
            } == true
        val discNumbersConflict = expected.discNumber != null &&
            current.discNumber != null &&
            expected.discNumber != current.discNumber

        if (!titleMatches) {
            // The old guard treated two non-empty titles as different tracks
            // even when the player was only replacing a queue description
            // with the canonical title. Keep that transition attached to the
            // already-spoken track when album/track or queue position proves
            // that it is the same item.
            if (hasTemporaryTitle &&
                (
                    sameAlbum && sameTrackNumber && sameDiscNumber && reliableTrackNumber ||
                        sameQueueItem(expected, current)
                    )
            ) {
                return true
            }
            if (!expected.title.isNullOrBlank() && !current.title.isNullOrBlank()) return false
            return matches(expected, current, requireSameSource)
        }

        // A provider can refresh the media ID while correcting optional
        // metadata. Valid, conflicting track/disc numbers are the one strong
        // signal that this is actually another queue item. Ignore invalid
        // values such as the transient 0 often emitted before the real number.
        if (validTrackNumbersConflict || discNumbersConflict) return false

        if (expectedMediaId == null && currentMediaId == null) {
            val trackConflict = expected.trackNumber != null &&
                current.trackNumber != null &&
                expected.trackNumber != current.trackNumber
            val discConflict = expected.discNumber != null &&
                current.discNumber != null &&
                expected.discNumber != current.discNumber
            if (trackConflict || discConflict) return false
        }
        return true
    }

    private fun sameQueueItem(expected: PlaybackEvent, current: PlaybackEvent): Boolean {
        val expectedPosition = expected.activeQueuePosition ?: return false
        val currentPosition = current.activeQueuePosition ?: return false
        if (expectedPosition != currentPosition) return false
        val expectedQueue = expected.queueTitle.normalizedOrNull() ?: return false
        val currentQueue = current.queueTitle.normalizedOrNull() ?: return false
        return expectedQueue == currentQueue
    }

    private fun isTemporaryTitle(value: String?): Boolean =
        PlaybackCollectionResolver.isGenericQueueTitle(value)

    private fun sameText(expected: String?, current: String?): Boolean =
        !expected.isNullOrBlank() && !current.isNullOrBlank() && normalize(expected) == normalize(current)

    private fun compatibleText(expected: String?, current: String?): Boolean =
        expected.isNullOrBlank() || current.isNullOrBlank() || normalize(expected) == normalize(current)

    private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

class DuplicateSuppressor(
    private val speechDuplicateCooldownMs: Long = 12_000L,
) {
    // Keep the bounded history for diagnostics and future stale-event
    // analysis, but never consult it to reject a normal A -> B -> A return.
    private val announcedTracks = linkedMapOf<String, AnnouncedTrack>()
    /** The only occurrence that can suppress the next automatic announcement. */
    private var currentOccurrence: AnnouncedTrack? = null
    private var lastSpeech: SpokenAnnouncement? = null

    fun shouldAnnounce(
        event: PlaybackEvent,
        allowRepeat: Boolean,
        now: Long,
        announcementText: String? = null,
        isNewPlaybackOccurrence: Boolean = false,
        isNewRepeatCycle: Boolean = false,
    ): Boolean {
        if (!event.hasTitle && event.mediaId.isNullOrBlank()) return false
        val current = currentOccurrence
        if (isNewPlaybackOccurrence || (isNewRepeatCycle && allowRepeat)) return true
        if (lastSpeech?.isDuplicateOf(event, announcementText, now, speechDuplicateCooldownMs) == true) {
            return false
        }
        if (current == null || !current.isSameTrack(event)) return true

        // Speech-text de-duplication remains a short safety net for callbacks
        // that arrive before a new track has been accepted. Explicit playback
        // transitions bypass it above.
        // The setting controls a genuine repeat-one cycle only. It does not
        // turn the whole listening history into a blacklist.
        return allowRepeat && isNewRepeatCycle
    }

    fun markAnnounced(event: PlaybackEvent, now: Long, announcementText: String? = null) {
        val fingerprint = TrackFingerprint.announcement(event)
        val track = AnnouncedTrack.from(event, now)
        announcedTracks[fingerprint] = track
        currentOccurrence = track
        if (!announcementText.isNullOrBlank()) {
            lastSpeech = SpokenAnnouncement(
                track = track,
                normalizedText = normalize(announcementText),
                announcedAt = now,
            )
        }
        while (announcedTracks.size > 64) {
            announcedTracks.remove(announcedTracks.keys.first())
        }
    }

    /**
     * Restores the last accepted track after the Android process is recreated.
     * Controller/session objects are disposable infrastructure; announcement
     * history must not be tied to their lifetime.
     */
    fun restoreAnnounced(event: PlaybackEvent, now: Long) {
        announcedTracks.clear()
        lastSpeech = null
        val track = AnnouncedTrack.from(event, now)
        announcedTracks[TrackFingerprint.announcement(event)] = track
        currentOccurrence = track
    }

    fun clear() {
        announcedTracks.clear()
        currentOccurrence = null
        lastSpeech = null
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private data class AnnouncedTrack(
        val event: PlaybackEvent,
        val announcedAt: Long,
    ) {
        companion object {
            fun from(event: PlaybackEvent, announcedAt: Long) = AnnouncedTrack(
                event = event,
                announcedAt = announcedAt,
            )
        }

        /**
         * A later callback that only fills a missing optional field is still
         * the same track. Two explicit, different track/disc values indicate
         * a real queue item change and must remain announceable.
         */
        fun isSameTrack(event: PlaybackEvent, requireSameSource: Boolean = true): Boolean {
            return AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                expected = this.event,
                current = event,
                requireSameSource = requireSameSource,
            )
        }

        fun isSameContent(event: PlaybackEvent, requireSameSource: Boolean = true): Boolean {
            return AnnouncementTrackMatcher.sameContent(
                expected = this.event,
                current = event,
                requireSameSource = requireSameSource,
            )
        }
    }

    private data class SpokenAnnouncement(
        val track: AnnouncedTrack,
        val normalizedText: String,
        val announcedAt: Long,
    ) {
        fun isDuplicateOf(
            event: PlaybackEvent,
            text: String?,
            now: Long,
            cooldownMs: Long,
        ): Boolean =
            !text.isNullOrBlank() &&
                now - announcedAt in 0 until cooldownMs &&
                (
                    normalizedText == normalize(text) ||
                    track.isSameContent(event, requireSameSource = false) ||
                    AnnouncementTrackMatcher.matchesForDuplicateSuppression(
                        expected = track.event,
                        current = event,
                        requireSameSource = false,
                    )
                    )

        private fun normalize(value: String): String =
            value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }
}
