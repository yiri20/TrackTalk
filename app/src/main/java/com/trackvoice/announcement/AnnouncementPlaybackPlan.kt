package com.trackvoice.announcement

import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.UserSettings

data class AnnouncementPlaybackPlan(
    val musicTreatment: MusicTreatment,
    val pauseBeforeAnnouncement: Boolean,
    val requestAudioFocus: Boolean,
    val shouldDuckMusic: Boolean,
)

object AnnouncementPlaybackPlanner {
    fun plan(settings: UserSettings): AnnouncementPlaybackPlan {
        // "음악과 함께 안내" must never inherit an old PAUSE selection.
        val treatment = settings.musicTreatment.takeUnless {
            it == MusicTreatment.PAUSE && settings.trackStartBehavior == TrackStartBehavior.PLAY_IMMEDIATELY
        } ?: MusicTreatment.DUCK
        return AnnouncementPlaybackPlan(
            musicTreatment = treatment,
            pauseBeforeAnnouncement = settings.trackStartBehavior == TrackStartBehavior.ANNOUNCE_THEN_PLAY,
            requestAudioFocus = treatment != MusicTreatment.KEEP,
            shouldDuckMusic = treatment == MusicTreatment.DUCK,
        )
    }
}
