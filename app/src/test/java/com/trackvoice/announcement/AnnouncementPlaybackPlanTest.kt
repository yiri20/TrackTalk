package com.trackvoice.announcement

import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementPlaybackPlanTest {
    @Test
    fun playImmediatelyNeverPausesEvenIfOldPauseSettingRemains() {
        val plan = AnnouncementPlaybackPlanner.plan(
            UserSettings(
                trackStartBehavior = TrackStartBehavior.PLAY_IMMEDIATELY,
                musicTreatment = MusicTreatment.PAUSE,
            ),
        )

        assertEquals(MusicTreatment.DUCK, plan.musicTreatment)
        assertFalse(plan.pauseBeforeAnnouncement)
        assertTrue(plan.requestAudioFocus)
        assertTrue(plan.shouldDuckMusic)
    }

    @Test
    fun announceThenPlayPausesAndRestoresWithKeepMusicTreatment() {
        val plan = AnnouncementPlaybackPlanner.plan(
            UserSettings(
                trackStartBehavior = TrackStartBehavior.ANNOUNCE_THEN_PLAY,
                musicTreatment = MusicTreatment.KEEP,
            ),
        )

        assertEquals(MusicTreatment.PAUSE, plan.musicTreatment)
        assertTrue(plan.pauseBeforeAnnouncement)
        assertTrue(plan.requestAudioFocus)
        assertFalse(plan.shouldDuckMusic)
        assertEquals(MusicAttenuationStrategy.MEDIA_PAUSE, plan.musicAttenuationStrategy)
    }

    @Test
    fun playImmediatelyLowerMusicUsesSystemDuckWithoutManualStreamMutation() {
        val plan = AnnouncementPlaybackPlanner.plan(
            UserSettings(
                trackStartBehavior = TrackStartBehavior.PLAY_IMMEDIATELY,
                musicTreatment = MusicTreatment.DUCK,
                musicDuckPercent = 50,
            ),
        )

        assertEquals(MusicAttenuationStrategy.SYSTEM_DUCK, plan.musicAttenuationStrategy)
        assertTrue(plan.shouldDuckMusic)
        assertTrue(plan.requestAudioFocus)
    }
}
