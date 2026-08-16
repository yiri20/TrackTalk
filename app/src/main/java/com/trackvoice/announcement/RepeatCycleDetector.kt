package com.trackvoice.announcement

import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.RepeatMode
import kotlin.math.max
import kotlin.math.min

/**
 * Detects only a well-supported repeat-one boundary. A position reset by
 * itself is not enough: seeking to the beginning must not be treated as a
 * new playback occurrence. The player must explicitly report repeat-one and
 * the preceding snapshot must be close to the end of the same track.
 */
object RepeatCycleDetector {
    private const val MAX_END_TOLERANCE_MS = 5_000L
    private const val MIN_END_TOLERANCE_MS = 1_000L
    private const val START_TOLERANCE_MS = 2_000L

    fun isNewRepeatOneCycle(previous: PlaybackEvent, current: PlaybackEvent): Boolean {
        if (current.repeatMode != RepeatMode.ONE) return false
        if (!AnnouncementTrackMatcher.matchesForDuplicateSuppression(previous, current)) return false

        val durationMs = current.duration ?: previous.duration ?: return false
        if (durationMs <= 0L) return false
        val previousPosition = previous.playbackPosition ?: return false
        val currentPosition = current.playbackPosition ?: return false
        if (previousPosition < 0L || currentPosition < 0L) return false

        val endToleranceMs = min(
            MAX_END_TOLERANCE_MS,
            max(MIN_END_TOLERANCE_MS, durationMs / 20L),
        )
        return previousPosition >= durationMs - endToleranceMs &&
            currentPosition <= START_TOLERANCE_MS &&
            previousPosition - currentPosition > START_TOLERANCE_MS
    }
}
