package com.trackvoice.announcement

import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSuppressorTest {
    @Test
    fun sameMetadataEventIsSuppressed() {
        val suppressor = DuplicateSuppressor()
        val event = event()
        assertTrue(suppressor.shouldAnnounce(event, allowRepeat = false, now = 1_000L))
        suppressor.markAnnounced(event, 1_000L)
        assertFalse(suppressor.shouldAnnounce(event.copy(observedAt = 2_000L), false, 2_000L))
    }

    @Test
    fun metadataEnrichmentForSameMediaIdIsSuppressed() {
        val suppressor = DuplicateSuppressor()
        val early = event().copy(
            title = null,
            artist = null,
            album = null,
            trackNumber = null,
            totalTracks = null,
            discNumber = null,
        )
        val enriched = event()

        assertTrue(suppressor.shouldAnnounce(early, allowRepeat = false, now = 1_000L))
        suppressor.markAnnounced(early, 1_000L)
        assertFalse(suppressor.shouldAnnounce(enriched, allowRepeat = false, now = 2_000L))
    }

    @Test
    fun sameTrackFromDifferentAppsIsASeparateEvent() {
        val suppressor = DuplicateSuppressor()
        val spotify = event("com.spotify.music")
        val youtube = event("com.google.android.apps.youtube.music")
        suppressor.markAnnounced(spotify, 1_000L)
        assertTrue(suppressor.shouldAnnounce(youtube, false, 2_000L))
    }

    @Test
    fun pauseAndResumeDoesNotAnnounceAgain() {
        val suppressor = DuplicateSuppressor()
        val playing = event().copy(playbackState = PlaybackStatus.PLAYING, playbackPosition = 10_000L)
        val paused = playing.copy(playbackState = PlaybackStatus.PAUSED, playbackPosition = 20_000L)
        suppressor.markAnnounced(playing, 1_000L)
        assertFalse(suppressor.shouldAnnounce(paused, false, 2_000L))
        assertFalse(suppressor.shouldAnnounce(playing, false, 3_000L))
    }

    @Test
    fun repeatModeStillHasCooldown() {
        val suppressor = DuplicateSuppressor(repeatCooldownMs = 5_000L)
        val event = event()
        suppressor.markAnnounced(event, 1_000L)
        assertFalse(suppressor.shouldAnnounce(event, true, 4_000L))
        assertTrue(suppressor.shouldAnnounce(event, true, 6_000L))
    }

    @Test
    fun differentDiscOrTrackNumberIsNotTheSameFingerprint() {
        val suppressor = DuplicateSuppressor()
        val first = event().copy(mediaId = null, trackNumber = 1, discNumber = 1)
        val second = event().copy(mediaId = null, trackNumber = 1, discNumber = 2)

        suppressor.markAnnounced(first, 1_000L)

        assertTrue(suppressor.shouldAnnounce(second, false, 2_000L))
    }

    private fun event(packageName: String = "com.spotify.music") = PlaybackEvent(
        sourcePackageName = packageName,
        sourceAppName = packageName,
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
        playbackPosition = 0L,
        observedAt = 1L,
    )
}
