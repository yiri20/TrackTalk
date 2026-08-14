package com.trackvoice.announcement

/**
 * Keeps music protection ahead of TTS. A media player can require a short
 * metadata-settlement wait before the final announcement text is known, but
 * that wait must not become a window in which full-volume music leaks through.
 */
object AnnouncementAudioTiming {
    const val PREPARATION_LEAD_MS = 180L

    fun preparationDelayMs(
        scheduledDelayMs: Long,
        decisionDelayMs: Long,
    ): Long {
        // A zero decision delay means "read now". Metadata settlement may add
        // time to scheduledDelayMs, but audio must be protected immediately.
        if (decisionDelayMs <= 0L) return 0L
        return (scheduledDelayMs - PREPARATION_LEAD_MS).coerceAtLeast(0L)
    }
}
