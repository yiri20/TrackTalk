package com.trackvoice.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackCollectionResolverTest {
    @Test
    fun albumAndTrackMetadataAloneDoesNotIdentifyDirectTrackAsAlbum() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(event(album = "Album", trackNumber = 2, totalTracks = 10)),
        )
    }

    @Test
    fun oneItemQueueWithAlbumMetadataDoesNotIdentifyDirectTrackAsAlbum() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    trackNumber = 2,
                    totalTracks = 10,
                    queueTitle = "다음 트랙",
                    queueSize = 1,
                ),
            ),
        )
    }

    @Test
    fun oneItemQueueWithAlbumItemMetadataDoesNotIdentifyDirectTrackAsAlbum() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    queueTitle = "다음 트랙",
                    queueSize = 1,
                ).copy(
                    queue = listOf(
                        QueueItemSnapshot(
                            mediaId = "song-1",
                            title = "Song",
                            artist = "Artist",
                            album = "Album",
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun directTrackAfterAlbumDoesNotInheritThePreviousAlbumContext() {
        val previous = event(
            album = "Album",
            trackNumber = 1,
            totalTracks = 10,
            queueTitle = "Album",
            queueSize = 10,
        )
        val current = event(
            album = "Another Album",
            trackNumber = 1,
            totalTracks = 12,
        ).copy(mediaId = "single-track")

        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event = current,
                previousEvent = previous,
                previousCollection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun genericMultiItemQueueWithAlbumMetadataRemainsAmbiguous() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    queueTitle = "다음 트랙",
                    activeQueuePosition = 2,
                    queueSize = 10,
                ),
            ),
        )
    }

    @Test
    fun directSingleTrackWithProviderGeneratedQueueRemainsUnknown() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    trackNumber = 1,
                    totalTracks = 10,
                    queueTitle = "다음 트랙",
                    queueSize = 25,
                    activeQueuePosition = 0,
                ).copy(mediaId = "direct-song"),
            ),
        )
    }

    @Test
    fun albumMetadataWithOnlyQueuePositionRemainsUnknownWithoutGenericQueueContext() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(album = "Album", activeQueuePosition = 2, queueSize = 10),
            ),
        )
    }

    @Test
    fun algorithmicQueueTitleStillIdentifiesAlgorithmicPlayback() {
        val event = event(
            album = "Album",
            queueTitle = "Daily Mix 1",
            queueSize = 10,
        ).copy(
            queue = List(10) { index ->
                QueueItemSnapshot(
                    mediaId = "track-$index",
                    title = "Song $index",
                    artist = "Artist",
                    album = "Album",
                )
            },
        )
        assertEquals(
            PlaybackCollection.ALGORITHMIC,
            PlaybackCollectionResolver.resolve(event),
        )
    }

    @Test
    fun lastAlbumTrackFollowedByAutoRecommendationBecomesAlgorithmic() {
        val previous = event(
            album = "Album",
            trackNumber = 10,
            totalTracks = 10,
            queueTitle = "다음 트랙",
            queueSize = 10,
        )
        val current = event(
            album = "Recommended Album",
            trackNumber = 2,
            totalTracks = 12,
            queueTitle = "다음 트랙",
            queueSize = 8,
        ).copy(mediaId = "recommended-song")

        assertEquals(
            PlaybackCollection.ALGORITHMIC,
            PlaybackCollectionResolver.resolve(
                event = current,
                previousEvent = previous,
                previousCollection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun staleAlbumQueueTitleDoesNotHideAutoRecommendationTransition() {
        val previous = event(
            album = "Album",
            trackNumber = 10,
            totalTracks = 10,
            queueTitle = "Album",
            queueSize = 10,
        )
        val current = event(
            album = "Recommended Album",
            trackNumber = 1,
            totalTracks = 8,
            queueTitle = "Album",
            queueSize = 5,
        ).copy(mediaId = "recommended-song")

        assertEquals(
            PlaybackCollection.ALGORITHMIC,
            PlaybackCollectionResolver.resolve(
                event = current,
                previousEvent = previous,
                previousCollection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun explicitPlaylistAfterAlbumRemainsPlaylist() {
        val previous = event(
            album = "Album",
            trackNumber = 10,
            totalTracks = 10,
            queueTitle = "다음 트랙",
            queueSize = 10,
        )
        val current = event(
            album = "Recommended Album",
            trackNumber = 2,
            totalTracks = 12,
            queueTitle = "Workout playlist",
            queueSize = 8,
        ).copy(mediaId = "playlist-song")

        assertEquals(
            PlaybackCollection.PLAYLIST,
            PlaybackCollectionResolver.resolve(
                event = current,
                previousEvent = previous,
                previousCollection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun albumMetadataWithoutARealQueueRemainsUnknown() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    queueTitle = "다음 트랙",
                    activeQueuePosition = 2,
                ),
            ),
        )
    }

    @Test
    fun playlistTitleWinsOverAlbumLikeTrackMetadata() {
        assertEquals(
            PlaybackCollection.PLAYLIST,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    trackNumber = 2,
                    totalTracks = 10,
                    queueTitle = "Workout playlist",
                ),
            ),
        )
    }

    @Test
    fun namedQueueDifferentFromAlbumIdentifiesPlaylist() {
        assertEquals(
            PlaybackCollection.PLAYLIST,
            PlaybackCollectionResolver.resolve(
                event(album = "Album", queueTitle = "출근길", queueSize = 10),
            ),
        )
    }

    @Test
    fun queueItemsFromOneAlbumIdentifyAlbum() {
        val event = event(album = "Album", queueTitle = "다음 트랙", queueSize = 3).copy(
            queue = List(3) { index ->
                QueueItemSnapshot(
                    mediaId = "track-$index",
                    title = "Song $index",
                    artist = "Artist",
                    album = "Album",
                    trackNumber = index + 1,
                )
            },
        )

        assertEquals(PlaybackCollection.ALBUM, PlaybackCollectionResolver.resolve(event))
    }

    @Test
    fun sameAlbumQueueWithoutExplicitTrackNumbersRemainsAmbiguous() {
        val event = event(album = "Album", queueTitle = "다음 트랙", queueSize = 3).copy(
            queue = List(3) { index ->
                QueueItemSnapshot(
                    mediaId = "track-$index",
                    title = "Song $index",
                    artist = "Artist",
                    album = "Album",
                )
            },
        )

        assertEquals(PlaybackCollection.UNKNOWN, PlaybackCollectionResolver.resolve(event))
    }

    @Test
    fun contextDecisionExplainsAmbiguousGenericQueue() {
        val decision = PlaybackCollectionResolver.resolveWithEvidence(
            event(album = "Album", queueTitle = "다음 트랙", queueSize = 25),
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
        assertEquals("AMBIGUOUS_MEDIA_SESSION_CONTEXT", decision.reason)
        assertEquals("GENERIC", decision.evidence.queueTitleSignal)
        assertFalse(decision.evidence.hasCanonicalAlbumQueue)
    }

    @Test
    fun queueItemsFromMultipleAlbumsIdentifyPlaylist() {
        val event = event(album = "Album", queueTitle = "다음 트랙", queueSize = 3).copy(
            queue = listOf(
                QueueItemSnapshot("track-1", "Song 1", "Artist", album = "Album"),
                QueueItemSnapshot("track-2", "Song 2", "Artist", album = "Other Album"),
                QueueItemSnapshot("track-3", "Song 3", "Artist", album = "Album"),
            ),
        )

        assertEquals(PlaybackCollection.PLAYLIST, PlaybackCollectionResolver.resolve(event))
    }

    @Test
    fun algorithmicTitleIdentifiesMixAsAlgorithmicPlayback() {
        assertEquals(
            PlaybackCollection.ALGORITHMIC,
            PlaybackCollectionResolver.resolve(event(queueTitle = "Daily Mix 1")),
        )
        assertEquals(
            PlaybackCollection.ALGORITHMIC,
            PlaybackCollectionResolver.resolve(event(queueTitle = "추천 라디오")),
        )
        assertEquals(
            PlaybackCollection.ALGORITHMIC,
            PlaybackCollectionResolver.resolve(event(queueTitle = "Random")),
        )
    }

    @Test
    fun shuffleStateAloneDoesNotIdentifyAlgorithmicPlayback() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(queueTitle = "Up next", shuffleState = ShuffleState.ON),
            ),
        )
    }

    @Test
    fun genericShuffleTitleDoesNotInferAlgorithmicPlayback() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(queueTitle = "Shuffle", shuffleState = ShuffleState.ON),
            ),
        )
    }

    @Test
    fun genericShuffleTitleWithQueueItemsIsNotMistakenForPlaylist() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(queueTitle = "Mix", queueSize = 8, shuffleState = ShuffleState.UNKNOWN),
            ),
        )
    }

    @Test
    fun explicitPlaylistStillWinsWhenPlaylistIsShuffled() {
        assertEquals(
            PlaybackCollection.PLAYLIST,
            PlaybackCollectionResolver.resolve(
                event(queueTitle = "Workout playlist", shuffleState = ShuffleState.ON),
            ),
        )
    }

    @Test
    fun shuffledAlbumMetadataWithoutQueueMetadataRemainsAmbiguous() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    trackNumber = 2,
                    totalTracks = 10,
                    queueTitle = "Up next",
                    queueSize = 10,
                    shuffleState = ShuffleState.ON,
                ).copy(
                    queue = List(10) { index ->
                        QueueItemSnapshot(
                            mediaId = "track-$index",
                            title = "Song $index",
                            artist = "Artist",
                            album = "Album",
                        )
                    },
                ),
            ),
        )
    }

    @Test
    fun albumMetadataWithGenericQueueAndNoQueueExtrasRemainsAmbiguous() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    trackNumber = 2,
                    totalTracks = 10,
                    queueTitle = "Up next",
                    queueSize = 10,
                    shuffleState = ShuffleState.ON,
                ),
            ),
        )
    }

    @Test
    fun shuffledPlaylistMetadataStillIdentifiesPlaylist() {
        assertEquals(
            PlaybackCollection.PLAYLIST,
            PlaybackCollectionResolver.resolve(
                event(
                    album = "Album",
                    queueTitle = "Up next",
                    queueSize = 3,
                    shuffleState = ShuffleState.ON,
                ).copy(
                    queue = listOf(
                        QueueItemSnapshot("track-1", "Song 1", "Artist", album = "Album"),
                        QueueItemSnapshot("track-2", "Song 2", "Artist", album = "Other Album"),
                        QueueItemSnapshot("track-3", "Song 3", "Artist", album = "Album"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun noCollectionSignalRemainsUnknown() {
        assertEquals(PlaybackCollection.UNKNOWN, PlaybackCollectionResolver.resolve(event()))
    }

    private fun event(
        album: String? = null,
        trackNumber: Int? = null,
        totalTracks: Int? = null,
        queueTitle: String? = null,
        activeQueuePosition: Int? = null,
        queueSize: Int = 0,
        shuffleState: ShuffleState = ShuffleState.UNKNOWN,
    ) = PlaybackEvent(
        sourcePackageName = "com.example.player",
        sourceAppName = "Player",
        title = "Song",
        artist = "Artist",
        album = album,
        albumArtist = null,
        trackNumber = trackNumber,
        totalTracks = totalTracks,
        discNumber = null,
        duration = null,
        mediaId = "song-1",
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = 0L,
        queue = List(queueSize) { QueueItemSnapshot("track-$it", "Song $it", "Artist") },
        observedAt = 1L,
        queueTitle = queueTitle,
        activeQueuePosition = activeQueuePosition,
        shuffleState = shuffleState,
    )
}
