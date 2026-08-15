package com.trackvoice.metadata

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItunesTrackMetadataResolverInstrumentedTest {
    private val resolver = ItunesTrackMetadataResolver()

    @Test
    fun parsesCanonicalTrackFields() {
        val candidates = resolver.parseCandidates(
            """
            {
              "resultCount": 1,
              "results": [{
                "wrapperType": "track",
                "kind": "song",
                "trackName": "Chicago",
                "artistName": "Sufjan Stevens",
                "collectionName": "Illinois",
                "trackNumber": 4,
                "trackCount": 22,
                "discNumber": 1,
                "discCount": 1,
                "trackTimeMillis": 342000
              }]
            }
            """.trimIndent(),
        )

        assertEquals(1, candidates.size)
        assertEquals("Chicago", candidates.single().title)
        assertEquals("Sufjan Stevens", candidates.single().artist)
        assertEquals("Illinois", candidates.single().album)
        assertEquals(4, candidates.single().trackNumber)
        assertEquals(22, candidates.single().trackCount)
        assertEquals(1, candidates.single().discNumber)
        assertEquals(342000L, candidates.single().durationMs)
    }

    @Test
    fun ignoresNonSongResultsAndMalformedEntries() {
        val candidates = resolver.parseCandidates(
            """
            {
              "resultCount": 3,
              "results": [
                {"wrapperType": "collection", "collectionName": "Illinois"},
                {"wrapperType": "track", "kind": "feature-movie", "trackName": "Not a song"},
                {"wrapperType": "track", "kind": "song", "trackName": "", "trackNumber": 0},
                null
              ]
            }
            """.trimIndent(),
        )

        assertTrue(candidates.isEmpty())
    }

    @Test
    fun malformedOrEmptyResponsesProduceNoCandidates() {
        assertTrue(resolver.parseCandidates("not-json").isEmpty())
        assertTrue(resolver.parseCandidates("{\"resultCount\":0,\"results\":[]}").isEmpty())
        assertTrue(resolver.parseCandidates("{}").isEmpty())
    }
}
