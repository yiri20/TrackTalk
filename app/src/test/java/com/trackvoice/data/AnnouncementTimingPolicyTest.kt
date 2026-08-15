package com.trackvoice.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementTimingPolicyTest {
    @Test
    fun immediateModeAlwaysHasZeroEffectiveDelay() {
        val settings = UserSettings(
            timing = AnnouncementTiming.IMMEDIATE,
            delaySeconds = 2,
        )

        assertEquals(0, AnnouncementTimingPolicy.effectiveDelaySeconds(settings))
        assertEquals(0L, AnnouncementTimingPolicy.effectiveDelayMs(settings))
    }

    @Test
    fun delayedModeNormalizesLegacyZeroToOneSecond() {
        assertEquals(
            AnnouncementTimingPolicy.MIN_DELAY_SECONDS,
            AnnouncementTimingPolicy.normalizeStoredDelaySeconds(AnnouncementTiming.DELAYED, 0),
        )
        assertEquals(
            1_000L,
            AnnouncementTimingPolicy.effectiveDelayMs(
                UserSettings(timing = AnnouncementTiming.DELAYED, delaySeconds = 0),
            ),
        )
    }

    @Test
    fun delayedModePreservesSelectedTwoSecondDelay() {
        assertEquals(
            2_000L,
            AnnouncementTimingPolicy.effectiveDelayMs(
                UserSettings(timing = AnnouncementTiming.DELAYED, delaySeconds = 2),
            ),
        )
    }

    @Test
    fun announceThenPlayUsesTheSelectedDelayWhenTimingIsDelayed() {
        assertEquals(
            2_000L,
            AnnouncementTimingPolicy.effectiveDelayMs(
                UserSettings(
                    timing = AnnouncementTiming.DELAYED,
                    delaySeconds = 2,
                    trackStartBehavior = TrackStartBehavior.ANNOUNCE_THEN_PLAY,
                ),
            ),
        )
    }
}
