package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AppSettings
import com.trackvoice.data.UserSettings
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementPolicyTest {
    @Test
    fun appSettingTakesPriorityOverGlobalMode() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(defaultMode = AnnouncementMode.TITLE_ONLY, suppressDuringSpeakerPlayback = false),
            appSettings = AppSettings("com.youtube.music", "YouTube Music", mode = AnnouncementMode.TITLE_AND_ARTIST),
            externalAudioOutput = true,
        )
        assertTrue(decision.shouldAnnounce)
        assertEquals("Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun smartUsesTrackNumberOnlyWhenAlbumContextIsPresent() {
        val smart = AnnouncementPolicy.decide(
            event(),
            UserSettings(suppressDuringSpeakerPlayback = false),
            null,
            externalAudioOutput = true,
        )
        val noAlbum = AnnouncementPolicy.decide(
            event().copy(album = null),
            UserSettings(suppressDuringSpeakerPlayback = false),
            null,
            externalAudioOutput = true,
        )
        assertEquals("트랙 3번, Glass Eyes.", smart.text)
        assertEquals("Glass Eyes, Radiohead.", noAlbum.text)
    }

    @Test
    fun speakerCanBeSuppressedWithoutTouchingPlayback() {
        val decision = AnnouncementPolicy.decide(
            event(),
            UserSettings(suppressDuringSpeakerPlayback = true),
            null,
            externalAudioOutput = false,
        )
        assertFalse(decision.shouldAnnounce)
        assertEquals(AnnouncementSkipReason.SPEAKER_OUTPUT, decision.skipReason)
    }

    @Test
    fun minimumPlaybackTimeDelaysAnnouncementInsteadOfDroppingIt() {
        val decision = AnnouncementPolicy.decide(
            event().copy(playbackPosition = 2_000L),
            UserSettings(
                suppressDuringSpeakerPlayback = false,
                minimumPlaybackSeconds = 5,
            ),
            null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(3_000L, decision.delayMs)
    }

    private fun event() = PlaybackEvent(
        sourcePackageName = "com.youtube.music",
        sourceAppName = "YouTube Music",
        title = "Glass Eyes",
        artist = "Radiohead",
        album = "A Moon Shaped Pool",
        albumArtist = "Radiohead",
        trackNumber = 3,
        totalTracks = 11,
        discNumber = 1,
        duration = 180_000L,
        mediaId = "track-3",
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = 2_000L,
        observedAt = 1L,
    )
}
