package com.trackvoice.announcement

import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.TrackFingerprint

class DuplicateSuppressor(
    private val repeatCooldownMs: Long = 30_000L,
) {
    private val announcedAtByFingerprint = linkedMapOf<String, Long>()

    fun shouldAnnounce(event: PlaybackEvent, allowRepeat: Boolean, now: Long): Boolean {
        val fingerprint = TrackFingerprint.stable(event)
        if (!event.hasTitle && event.mediaId.isNullOrBlank()) return false
        val previous = announcedAtByFingerprint[fingerprint]
        return when {
            previous == null -> true
            !allowRepeat -> false
            now - previous >= repeatCooldownMs -> true
            else -> false
        }
    }

    fun markAnnounced(event: PlaybackEvent, now: Long) {
        announcedAtByFingerprint[TrackFingerprint.stable(event)] = now
        while (announcedAtByFingerprint.size > 64) {
            announcedAtByFingerprint.remove(announcedAtByFingerprint.keys.first())
        }
    }

    fun clear() = announcedAtByFingerprint.clear()
}
