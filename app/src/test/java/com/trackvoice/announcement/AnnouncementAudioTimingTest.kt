package com.trackvoice.announcement

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementAudioTimingTest {
    @Test
    fun immediateAnnouncementProtectsAudioBeforeMetadataSettlementFinishes() {
        assertEquals(
            0L,
            AnnouncementAudioTiming.preparationDelayMs(
                scheduledDelayMs = 250L,
                decisionDelayMs = 0L,
            ),
        )
    }

    @Test
    fun delayedAnnouncementPreparesAudioBeforeTts() {
        assertEquals(
            1_820L,
            AnnouncementAudioTiming.preparationDelayMs(
                scheduledDelayMs = 2_000L,
                decisionDelayMs = 2_000L,
            ),
        )
    }

    @Test
    fun shortDelayedAnnouncementPreparesImmediately() {
        assertEquals(
            0L,
            AnnouncementAudioTiming.preparationDelayMs(
                scheduledDelayMs = 120L,
                decisionDelayMs = 120L,
            ),
        )
    }
}
