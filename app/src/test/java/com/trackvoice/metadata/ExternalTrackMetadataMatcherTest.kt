package com.trackvoice.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalTrackMetadataMatcherTest {
    @Test
    fun exactTitleArtistAlbumMatchReturnsCanonicalTrackNumber() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery(
                title = "Chicago",
                artist = "Sufjan Stevens",
                album = "Illinois",
                durationMs = 360_000L,
            ),
            candidates = listOf(
                candidate(
                    trackNumber = 4,
                    title = "Chicago",
                    artist = "Sufjan Stevens",
                    album = "Illinois",
                    durationMs = 360_100L,
                ),
            ),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.MATCHED, result.status)
        assertEquals(4, result.metadata?.trackNumber)
        assertTrue(result.confidence >= ExternalTrackMetadataMatcher.MIN_CONFIDENCE)
    }

    @Test
    fun durationHelpsChooseTheCorrectSameNamedRelease() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Song", "Artist", "Album", 200_000L),
            candidates = listOf(
                candidate(trackNumber = 1, durationMs = 230_000L),
                candidate(trackNumber = 7, durationMs = 201_000L),
            ),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.MATCHED, result.status)
        assertEquals(7, result.metadata?.trackNumber)
    }

    @Test
    fun editionSuffixesCanMatchWithoutRemovingMeaningfulTitleText() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Track (Remastered)", "Artist", "Album (Deluxe Edition)"),
            candidates = listOf(candidate(trackNumber = 3, title = "Track", album = "Album")),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.MATCHED, result.status)
        assertEquals(3, result.metadata?.trackNumber)
    }

    @Test
    fun albumMismatchIsRejectedRatherThanGuessingTrackNumber() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Song", "Artist", "Correct Album"),
            candidates = listOf(candidate(trackNumber = 8, album = "Different Album")),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.NOT_FOUND, result.status)
        assertEquals(null, result.metadata)
    }

    @Test
    fun equallyStrongCandidatesAreAmbiguous() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Song", "Artist"),
            candidates = listOf(
                candidate(trackNumber = 2, album = "Album A"),
                candidate(trackNumber = 9, album = "Album B"),
            ),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.AMBIGUOUS, result.status)
        assertEquals(null, result.metadata)
    }

    @Test
    fun multiDiscMetadataIsReturnedWithTheMatch() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Finale", "Artist", "Live"),
            candidates = listOf(
                candidate(
                    trackNumber = 4,
                    title = "Finale",
                    artist = "Artist",
                    album = "Live",
                    discNumber = 2,
                    discCount = 2,
                ),
            ),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.MATCHED, result.status)
        assertEquals(2, result.metadata?.discNumber)
        assertEquals(4, result.metadata?.trackNumber)
    }

    @Test
    fun missingOrWrongArtistDoesNotProduceANumber() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Song", "Expected Artist", "Album"),
            candidates = listOf(candidate(trackNumber = 1, artist = "Other Artist")),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.NOT_FOUND, result.status)
    }

    @Test
    fun titleOnlyQueriesDoNotReceiveAConfidentTrackNumber() {
        val result = ExternalTrackMetadataMatcher.match(
            query = ExternalTrackMetadataQuery("Song"),
            candidates = listOf(candidate(trackNumber = 2)),
            provider = "FAKE",
        )

        assertEquals(ExternalMetadataStatus.NOT_FOUND, result.status)
        assertEquals(null, result.metadata)
    }

    @Test
    fun negativeCacheEntriesHaveAFiniteLifetime() {
        val entry = ExternalTrackMetadataResult(
            status = ExternalMetadataStatus.AMBIGUOUS,
            provider = "FAKE",
        ).toCacheEntry(now = 1_000L)

        assertTrue(ExternalMetadataCachePolicy.isFresh(entry, 1_000L + 1_000L))
        assertEquals(
            false,
            ExternalMetadataCachePolicy.isFresh(
                entry,
                1_000L + ExternalMetadataCachePolicy.NEGATIVE_TTL_MS + 1L,
            ),
        )
    }

    private fun candidate(
        trackNumber: Int,
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long? = 200_000L,
        discNumber: Int? = 1,
        discCount: Int? = 1,
    ) = ExternalTrackMetadataCandidate(
        trackNumber = trackNumber,
        trackCount = 10,
        discNumber = discNumber,
        discCount = discCount,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
    )
}
