package com.trackvoice.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalPlaybackContextResolverTest {
    @Test
    fun firstAmbiguousTrackRemainsUnknown() {
        val resolver = TemporalPlaybackContextResolver()

        val decision = resolver.resolve(track(number = 1, observedAt = 1_000L), sessionKey = "session")

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals("INITIAL_AMBIGUOUS", decision.reason)
    }

    @Test
    fun oneSameAlbumNaturalTransitionIsNotEnoughToConfirmAlbum() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session")
        val decision = resolver.resolve(
            track(number = 2, observedAt = 181_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals(1, decision.sameAlbumNaturalTransitions)
        assertTrue(decision.naturalTransition)
    }

    @Test
    fun twoSameAlbumNaturalTransitionsConfirmAlbum() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 2, observedAt = 181_000L, position = 0L), "session")
        resolver.resolve(track(number = 2, observedAt = 350_000L, position = 170_000L), "session")
        val decision = resolver.resolve(
            track(number = 3, observedAt = 361_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.ALBUM, decision.collection)
        assertEquals("TEMPORAL_SAME_ALBUM_CONTINUITY", decision.reason)
        assertEquals(2, decision.sameAlbumNaturalTransitions)
    }

    @Test
    fun directSongThenDifferentAlbumDoesNotBecomeAlbum() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, album = "First Album", observedAt = 1_000L, position = 170_000L), "session")
        val decision = resolver.resolve(
            track(number = 1, album = "Recommended Album", observedAt = 181_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertFalse(decision.sameAlbumNaturalTransitions > 0)
    }

    @Test
    fun mixedAlbumSequenceDoesNotConfirmAlbum() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, album = "Album A", observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 2, album = "Album A", observedAt = 181_000L, position = 0L), "session")
        resolver.resolve(track(number = 2, album = "Album A", observedAt = 350_000L, position = 170_000L), "session")
        val decision = resolver.resolve(
            track(number = 3, album = "Album B", observedAt = 361_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals(0, decision.sameAlbumNaturalTransitions)
        assertEquals(1, decision.mixedNaturalTransitions)
    }

    @Test
    fun rapidManualSkipsDoNotEstablishAlbumConfidence() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 10_000L), "session")
        resolver.resolve(track(number = 2, observedAt = 2_000L, position = 0L), "session")
        resolver.resolve(track(number = 2, observedAt = 3_000L, position = 1_000L), "session")
        val decision = resolver.resolve(
            track(number = 3, observedAt = 4_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertFalse(decision.naturalTransition)
        assertEquals(0, decision.sameAlbumNaturalTransitions)
    }

    @Test
    fun sessionChangeDiscardsPreviousAlbumEvidence() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session-a")
        resolver.resolve(track(number = 2, observedAt = 181_000L, position = 0L), "session-a")
        val decision = resolver.resolve(
            track(number = 3, observedAt = 190_000L, position = 0L),
            "session-b",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertTrue(decision.stateReset)
        assertEquals("RESET_MEDIA_SESSION_CHANGED", decision.reason)
        assertEquals(0, decision.sameAlbumNaturalTransitions)
    }

    @Test
    fun explicitStopDiscardsPreviousAlbumEvidence() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 2, observedAt = 181_000L, position = 0L), "session")
        val stopped = track(number = 2, observedAt = 182_000L, position = 0L).copy(
            playbackState = PlaybackStatus.STOPPED,
        )
        resolver.resolve(stopped, "session")

        val decision = resolver.resolve(
            track(number = 3, observedAt = 200_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertTrue(decision.stateReset)
        assertEquals("RESET_PLAYBACK_SESSION_RESTARTED", decision.reason)
        assertEquals(0, decision.sameAlbumNaturalTransitions)
    }

    @Test
    fun shortStoppedBridgePreservesNaturalAlbumContinuity() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 2, observedAt = 181_000L, position = 0L), "session")
        resolver.resolve(
            track(number = 2, observedAt = 360_100L, position = 170_000L).copy(
                playbackState = PlaybackStatus.STOPPED,
            ),
            "session",
        )

        val decision = resolver.resolve(
            track(number = 3, observedAt = 361_000L, position = 0L),
            "session",
        )

        assertFalse(decision.stateReset)
        assertTrue(decision.naturalTransition)
        assertEquals(2, decision.sameAlbumNaturalTransitions)
        assertEquals(PlaybackCollection.ALBUM, decision.collection)
    }

    @Test
    fun providerStyleQueueRefreshDoesNotErasePlayingPredecessor() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(
            track(number = 1, observedAt = 1_100L, position = 170_000L).copy(
                queueTitle = "다음 트랙",
                queueOrderChanged = true,
            ),
            "session",
        )
        val decision = resolver.resolve(
            track(number = 2, observedAt = 181_000L, position = 0L).copy(
                queueTitle = "다음 트랙",
                queueOrderChanged = true,
            ),
            "session",
        )

        assertFalse(decision.stateReset)
        assertTrue(decision.naturalTransition)
        assertEquals(1, decision.sameAlbumNaturalTransitions)
    }

    @Test
    fun resetClearsTemporalAlbumContinuity() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 2, observedAt = 181_000L, position = 0L), "session")
        resolver.reset()

        val decision = resolver.resolve(
            track(number = 3, observedAt = 190_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals("INITIAL_AMBIGUOUS", decision.reason)
        assertEquals(0, decision.sameAlbumNaturalTransitions)
    }

    @Test
    fun confirmedAlbumCanBeRevokedByContradictoryNaturalTransition() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, album = "Album A", observedAt = 1_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 2, album = "Album A", observedAt = 181_000L, position = 0L), "session")
        resolver.resolve(track(number = 2, album = "Album A", observedAt = 350_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 3, album = "Album A", observedAt = 361_000L, position = 0L), "session")
        resolver.resolve(track(number = 3, album = "Album A", observedAt = 530_000L, position = 170_000L), "session")
        resolver.resolve(track(number = 4, album = "Album A", observedAt = 541_000L, position = 0L), "session")
        resolver.resolve(track(number = 4, album = "Album A", observedAt = 710_000L, position = 170_000L), "session")
        val decision = resolver.resolve(
            track(number = 5, album = "Album B", observedAt = 721_000L, position = 0L),
            "session",
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals("MIXED_ALBUM_TRANSITION", decision.reason)
    }

    @Test
    fun metadataRefreshForSameTrackDoesNotCountAsTransition() {
        val resolver = TemporalPlaybackContextResolver()

        resolver.resolve(track(number = 1, observedAt = 1_000L), "session")
        val decision = resolver.resolve(
            track(number = 1, observedAt = 100_000L).copy(album = "Album A"),
            "session",
        )

        assertFalse(decision.transition)
        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals(0, decision.sameAlbumNaturalTransitions)
    }

    private fun track(
        number: Int,
        album: String = "Album A",
        observedAt: Long,
        position: Long = 0L,
    ) = PlaybackEvent(
        sourcePackageName = "com.google.android.apps.youtube.music",
        sourceAppName = "YouTube Music",
        title = "Track $number",
        artist = "Artist",
        album = album,
        albumArtist = "Artist",
        trackNumber = number,
        totalTracks = 10,
        discNumber = 1,
        duration = 180_000L,
        mediaId = "track-$number",
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = position,
        observedAt = observedAt,
        activeQueuePosition = number - 1,
    )
}
