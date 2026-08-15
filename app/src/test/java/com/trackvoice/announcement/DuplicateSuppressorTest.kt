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
    fun metadataEnrichmentDoesNotReadTheSameTrackAgain() {
        val suppressor = DuplicateSuppressor()
        val early = event().copy(artist = null)
        val enriched = event()

        assertTrue(suppressor.shouldAnnounce(early, false, 1_000L))
        suppressor.markAnnounced(early, 1_000L)
        assertFalse(suppressor.shouldAnnounce(enriched, false, 2_000L))
    }

    @Test
    fun pendingTrackWithMissingTitleMatchesItsLaterEnrichment() {
        val early = event().copy(title = null)
        val enriched = event()

        assertTrue(AnnouncementTrackMatcher.matches(early, enriched))
        assertTrue(AnnouncementTrackMatcher.matches(enriched, early))
    }

    @Test
    fun skippedTrackDoesNotMatchPendingAlbumTrack() {
        val early = event().copy(title = null)
        val next = event().copy(
            title = "Next song",
            mediaId = "track-4",
            trackNumber = 4,
        )

        assertFalse(AnnouncementTrackMatcher.matches(early, next))
    }

    @Test
    fun trackNumberCorrectionForSameMediaIdDoesNotReadAgain() {
        val suppressor = DuplicateSuppressor()
        val early = event().copy(trackNumber = null)
        val corrected = event().copy(trackNumber = 3)

        suppressor.markAnnounced(early, 1_000L)

        assertFalse(suppressor.shouldAnnounce(corrected, false, 2_000L))
    }

    @Test
    fun changedMediaIdWithSameTrackMetadataDoesNotReadAgain() {
        val suppressor = DuplicateSuppressor()
        val early = event()
        val refreshed = event().copy(mediaId = "provider://track-3")

        suppressor.markAnnounced(early, 1_000L)

        assertFalse(suppressor.shouldAnnounce(refreshed, false, 2_000L))
    }

    @Test
    fun changedMediaIdAndCorrectedTrackNumberDoesNotReadAgain() {
        val suppressor = DuplicateSuppressor()
        val early = event().copy(trackNumber = 0)
        val corrected = event().copy(
            mediaId = "provider://canonical-track-3",
            trackNumber = 3,
        )

        suppressor.markAnnounced(early, 1_000L, announcementText = "Glass Eyes, Radiohead.")

        assertFalse(
            suppressor.shouldAnnounce(
                corrected,
                allowRepeat = false,
                now = 20_000L,
                announcementText = "트랙 3번, Glass Eyes, Radiohead.",
            ),
        )
    }

    @Test
    fun sameSongAfterControllerRecreationDoesNotReadAgain() {
        val suppressor = DuplicateSuppressor()
        val announced = event().copy(mediaId = "session-a-track-3")
        val refreshedController = announced.copy(mediaId = "session-b-track-3")

        suppressor.markAnnounced(announced, 1_000L, announcementText = "Glass Eyes, Radiohead.")

        assertFalse(
            suppressor.shouldAnnounce(
                refreshedController,
                allowRepeat = false,
                now = 2_000L,
                announcementText = "Glass Eyes, Radiohead.",
            ),
        )
    }

    @Test
    fun nextTrackAfterControllerRecreationRemainsAnnounceable() {
        val suppressor = DuplicateSuppressor()
        val announced = event().copy(mediaId = "session-a-track-3")
        val nextTrack = announced.copy(
            mediaId = "session-b-track-4",
            title = "Next song",
            trackNumber = 4,
        )

        suppressor.markAnnounced(announced, 1_000L)

        assertTrue(suppressor.shouldAnnounce(nextTrack, allowRepeat = false, now = 2_000L))
    }

    @Test
    fun queueDescriptionChangingToCanonicalTitleDoesNotReadAgain() {
        val suppressor = DuplicateSuppressor()
        val queueDescription = event().copy(
            title = "Up next",
            mediaId = "queue-item-3",
            activeQueuePosition = 2,
            queueTitle = "A Moon Shaped Pool",
        )
        val canonical = event().copy(
            mediaId = "provider://canonical-track-3",
            activeQueuePosition = 2,
            queueTitle = "A Moon Shaped Pool",
        )

        suppressor.markAnnounced(queueDescription, 1_000L)

        assertFalse(suppressor.shouldAnnounce(canonical, false, 2_000L))
    }

    @Test
    fun albumCorrectionWithSameTitleAndArtistDoesNotReadAgain() {
        val suppressor = DuplicateSuppressor()
        val first = event().copy(album = "Original album")
        val corrected = event().copy(
            album = "Compilation album",
            mediaId = "provider://refreshed-track-3",
        )

        suppressor.markAnnounced(first, 1_000L)

        assertFalse(suppressor.shouldAnnounce(corrected, false, 20_000L))
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
    fun identicalSpokenTextFromASecondEventIsSuppressedBriefly() {
        val suppressor = DuplicateSuppressor()
        val first = event().copy(title = "First song", mediaId = "track-1")
        val second = event().copy(title = "Second song", mediaId = "track-2")

        suppressor.markAnnounced(first, 1_000L, announcementText = "Now playing.")

        assertFalse(suppressor.shouldAnnounce(second, false, 2_000L, "Now playing."))
        assertFalse(suppressor.shouldAnnounce(second, false, 7_000L, "Now playing."))
        assertTrue(suppressor.shouldAnnounce(second, false, 13_001L, "Now playing."))
    }

    @Test
    fun sameTrackFromAnotherMediaSessionIsSuppressedWhileSpeechIsActive() {
        val suppressor = DuplicateSuppressor()
        val first = event("com.google.android.apps.youtube.music")
        val second = first.copy(
            sourcePackageName = "com.google.android.youtube",
            mediaId = "different-session-id",
            album = "Updated album metadata",
        )

        suppressor.markAnnounced(first, 1_000L, announcementText = "Glass Eyes, Radiohead.")

        assertFalse(
            suppressor.shouldAnnounce(
                second,
                allowRepeat = false,
                now = 4_000L,
                announcementText = "Glass Eyes, Radiohead, Album Updated album metadata.",
            ),
        )
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
