package com.trackvoice.announcement

import com.trackvoice.data.MusicTreatment
import com.trackvoice.data.TrackStartBehavior
import com.trackvoice.data.UserSettings

enum class MusicAttenuationStrategy {
    NONE,
    SYSTEM_DUCK,
    MEDIA_PAUSE,
}

data class AnnouncementPlaybackPlan(
    val musicTreatment: MusicTreatment,
    val pauseBeforeAnnouncement: Boolean,
    val requestAudioFocus: Boolean,
    val shouldDuckMusic: Boolean,
    val musicAttenuationStrategy: MusicAttenuationStrategy,
)

object AnnouncementPlaybackPlanner {
    fun plan(settings: UserSettings): AnnouncementPlaybackPlan {
        // "곡명 안내 후 재생" is itself the pause-until-speech-finishes mode.
        // Keep it consistent even for settings saved by an older build.
        val treatment = when (settings.trackStartBehavior) {
            TrackStartBehavior.ANNOUNCE_THEN_PLAY -> MusicTreatment.PAUSE
            TrackStartBehavior.PLAY_IMMEDIATELY -> settings.musicTreatment.takeUnless {
                it == MusicTreatment.PAUSE
            } ?: MusicTreatment.DUCK
        }
        return AnnouncementPlaybackPlan(
            musicTreatment = treatment,
            pauseBeforeAnnouncement = settings.trackStartBehavior == TrackStartBehavior.ANNOUNCE_THEN_PLAY,
            requestAudioFocus = treatment != MusicTreatment.KEEP,
            shouldDuckMusic = treatment == MusicTreatment.DUCK,
            musicAttenuationStrategy = when (treatment) {
                MusicTreatment.KEEP -> MusicAttenuationStrategy.NONE
                MusicTreatment.DUCK -> MusicAttenuationStrategy.SYSTEM_DUCK
                MusicTreatment.PAUSE -> MusicAttenuationStrategy.MEDIA_PAUSE
            },
        )
    }
}
