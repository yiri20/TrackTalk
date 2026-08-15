package com.trackvoice.data

/**
 * Keeps the announcement timing setting consistent between the UI, storage,
 * and runtime policy.
 *
 * A delayed announcement is an actual delay, so zero is not a valid visible
 * value for that mode. Immediate mode may retain a previously selected delay
 * in storage, but the runtime must always treat it as zero.
 */
object AnnouncementTimingPolicy {
    const val MIN_DELAY_SECONDS = 1
    const val MAX_DELAY_SECONDS = 2

    fun isDelayed(timing: AnnouncementTiming): Boolean = timing == AnnouncementTiming.DELAYED ||
        timing == AnnouncementTiming.BETWEEN_TRACKS

    fun normalizeStoredDelaySeconds(timing: AnnouncementTiming, value: Int): Int = if (isDelayed(timing)) {
        value.coerceIn(MIN_DELAY_SECONDS, MAX_DELAY_SECONDS)
    } else {
        value.coerceIn(0, MAX_DELAY_SECONDS)
    }

    fun effectiveDelaySeconds(settings: UserSettings): Int = if (!isDelayed(settings.timing)) {
        0
    } else {
        normalizeStoredDelaySeconds(settings.timing, settings.delaySeconds)
    }

    fun effectiveDelayMs(settings: UserSettings): Long = effectiveDelaySeconds(settings) * 1_000L
}
