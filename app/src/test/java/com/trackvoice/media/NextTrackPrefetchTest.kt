package com.trackvoice.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextTrackPrefetchTest {
    @Test
    fun queueExposesCompleteNextMetadata() {
        val prepared = NextTrackPrefetch.prepare(
            event = event(
                queue = listOf(
                    item(10, "Current", "Artist", "Album", 1),
                    item(11, "Next", "Artist", "Album", 2),
                ),
            ),
            sessionKey = "session-1",
            preparedAt = 1_000L,
        )

        assertEquals(NextTrackPrefetchQuality.FULL, prepared?.quality)
        assertEquals(11L, prepared?.predicted?.queueItemId)
        assertEquals(2, prepared?.trackNumber)
    }

    @Test
    fun queueWithTitleAndArtistCreatesSafePartialPrediction() {
        val next = item(11, "Next", "Artist")

        assertEquals(NextTrackPrefetchQuality.PARTIAL, NextTrackPrefetch.quality(next))
        assertTrue(NextTrackPrefetch.prepare(event(queue = listOf(item(10, "Current"), next)), "s", 1L) != null)
    }

    @Test
    fun queueWithoutUsefulMetadataRetainsReactivePath() {
        val noMetadata = item(queueItemId = null, title = null, artist = null)

        assertEquals(NextTrackPrefetchQuality.NONE, NextTrackPrefetch.quality(noMetadata))
        assertNull(NextTrackPrefetch.prepare(event(queue = listOf(item(10, "Current"), noMetadata)), "s", 1L))
    }

    @Test
    fun queueChangeInvalidatesOldPrediction() {
        val first = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current"), item(11, "Next", "Artist"))),
            "s",
            1L,
        )!!
        val replacement = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current"), item(12, "Replacement", "Artist"))),
            "s",
            2L,
        )!!

        assertFalse(NextTrackPrefetch.samePrediction(first, replacement))
    }

    @Test
    fun queueReplacementWithSameNextItemStillInvalidatesGeneration() {
        val first = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current"), item(11, "Next"), item(12, "Following"))),
            "s",
            1L,
        )!!
        val replacement = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current"), item(11, "Next"), item(99, "Different tail"))),
            "s",
            2L,
        )!!

        assertFalse(NextTrackPrefetch.samePrediction(first, replacement))
    }

    @Test
    fun rebuiltQueueIdsDoNotInvalidateTheSameContentPrediction() {
        val first = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current", "Artist"), item(11, "Next", "Artist"))),
            "s",
            1L,
        )!!
        val rebuilt = NextTrackPrefetch.prepare(
            event(queue = listOf(item(110, "Current", "Artist"), item(111, "Next", "Artist"))),
            "s",
            2L,
        )!!

        assertTrue(NextTrackPrefetch.samePrediction(first, rebuilt))
        assertTrue(NextTrackPrefetch.matches(first, event(
            title = "Next",
            artist = "Artist",
            queue = listOf(item(111, "Next", "Artist")),
        ), "s"))
    }

    @Test
    fun inconsistentActiveQueuePositionDoesNotCreatePrediction() {
        val event = event(
            title = "Current",
            artist = "Artist",
            queue = listOf(
                item(10, "Other", "Different Artist"),
                item(11, "Next", "Artist"),
            ),
        )

        assertNull(NextTrackPrefetch.prepare(event, "s", 1L))
    }

    @Test
    fun actualTransitionMatchingPreparedItemUsesItExactlyOnce() {
        val prepared = preparedForNext()
        val actual = event(
            title = "Next",
            artist = "Artist",
            queue = listOf(item(11, "Next", "Artist"), item(12, "Following", "Artist")),
        )

        assertTrue(NextTrackPrefetch.matches(prepared, actual, "s"))
        assertTrue(NextTrackPrefetch.matches(prepared, actual, "s"))
        assertFalse(NextTrackPrefetch.matches(prepared, actual, "other-session"))
    }

    @Test
    fun mismatchedTransitionDiscardsPredictionAndFallsBack() {
        val prepared = preparedForNext()
        val skipped = event(
            title = "Following",
            artist = "Artist",
            queue = listOf(item(12, "Following", "Artist")),
        )

        assertFalse(NextTrackPrefetch.matches(prepared, skipped, "s"))
    }

    @Test
    fun partialPredictionIsEnrichedByRealMetadataWithoutOverwritingIt() {
        val prepared = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current"), item(11, "Next"))),
            "s",
            1L,
        )!!
        val actual = event(
            title = "Next",
            artist = "Real Artist",
            album = "Real Album",
            queue = listOf(item(11, "Next", "Real Artist")),
        )

        val merged = NextTrackPrefetch.mergeMissingMetadata(prepared, actual)

        assertEquals("Next", merged.title)
        assertEquals("Real Artist", merged.artist)
        assertEquals("Real Album", merged.album)
    }

    @Test
    fun queuePositionNeverBecomesCanonicalTrackNumber() {
        val prepared = NextTrackPrefetch.prepare(
            event(queue = listOf(item(10, "Current"), item(11, "Next"))),
            "s",
            1L,
        )!!
        val actual = event(
            title = "Next",
            queue = listOf(item(11, "Next")),
        )

        assertNull(prepared.trackNumber)
        assertNull(NextTrackPrefetch.mergeMissingMetadata(prepared, actual).trackNumber)
    }

    @Test
    fun previousTrackDoesNotMatchThePreparedNextTrack() {
        val prepared = preparedForNext()
        val previous = event(
            title = "Previous",
            artist = "Artist",
            queue = listOf(item(9, "Previous", "Artist"), item(10, "Current", "Artist")),
        )

        assertFalse(NextTrackPrefetch.matches(prepared, previous, "s"))
    }

    private fun preparedForNext(): PreparedNextTrack = NextTrackPrefetch.prepare(
        event(queue = listOf(item(10, "Current", "Artist"), item(11, "Next", "Artist"))),
        "s",
        1L,
    )!!

    private fun event(
        title: String? = "Current",
        artist: String? = "Artist",
        album: String? = "Album",
        queue: List<QueueItemSnapshot>,
    ) = PlaybackEvent(
        sourcePackageName = "player",
        sourceAppName = "Player",
        title = title,
        artist = artist,
        album = album,
        albumArtist = null,
        trackNumber = null,
        totalTracks = null,
        discNumber = null,
        duration = 200_000L,
        mediaId = null,
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = 10_000L,
        queue = queue,
        observedAt = 1_000L,
        queueTitle = "Up next",
        activeQueuePosition = 0,
    )

    private fun item(
        queueItemId: Long? = 1L,
        title: String? = "Song",
        artist: String? = null,
        album: String? = null,
        trackNumber: Int? = null,
    ) = QueueItemSnapshot(
        mediaId = null,
        title = title,
        artist = artist,
        album = album,
        trackNumber = trackNumber,
        queueItemId = queueItemId,
    )
}
