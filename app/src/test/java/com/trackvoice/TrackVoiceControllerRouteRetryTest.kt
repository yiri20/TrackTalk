package com.trackvoice

import com.trackvoice.announcement.DuplicateSuppressor
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackVoiceControllerRouteRetryTest {
    @Test
    fun routeRetryOnlyResumesTheStillCurrentTrack() {
        val pendingA = event(title = "A", mediaId = "track-a")
        val currentB = event(title = "B", mediaId = "track-b")

        assertFalse(isRouteRetryStillCurrent(pendingA, currentB))
    }

    @Test
    fun routeRetryAcceptsMetadataEnrichmentForTheSameTrack() {
        val pendingA = event(title = "A", mediaId = "queue-a", artist = null)
        val enrichedA = event(title = "A", mediaId = "canonical-a", artist = "Artist")

        assertTrue(isRouteRetryStillCurrent(pendingA, enrichedA))
    }

    @Test
    fun routeRetryDropsAnIdenticalTrackFromAnotherApp() {
        val pendingA = event(title = "A", mediaId = "track-a")
        val otherAppA = pendingA.copy(sourcePackageName = "com.spotify.music")

        assertFalse(isRouteRetryStillCurrent(pendingA, otherAppA))
    }

    @Test
    fun resolvedRouteCandidateIsAcceptedOnlyOnceAcrossRepeatedCallbacks() {
        val suppressor = DuplicateSuppressor()
        val retriedA = event(title = "A", mediaId = "track-a")

        assertTrue(suppressor.shouldAnnounce(retriedA, allowRepeat = false, now = 1_000L))
        suppressor.markAnnounced(retriedA, now = 1_000L)

        assertFalse(
            suppressor.shouldAnnounce(
                retriedA.copy(observedAt = 1_001L),
                allowRepeat = false,
                now = 1_001L,
            ),
        )
    }

    private fun event(
        title: String,
        mediaId: String,
        artist: String? = "Artist",
    ) = PlaybackEvent(
        sourcePackageName = "com.google.android.apps.youtube.music",
        sourceAppName = "YouTube Music",
        title = title,
        artist = artist,
        album = "Album",
        albumArtist = null,
        trackNumber = null,
        totalTracks = null,
        discNumber = null,
        duration = 180_000L,
        mediaId = mediaId,
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = 0L,
        observedAt = 1L,
    )
}
