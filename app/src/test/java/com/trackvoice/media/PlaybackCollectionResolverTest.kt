package com.trackvoice.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCollectionResolverTest {
    @Test
    fun albumMetadataIdentifiesAlbum() {
        assertEquals(
            PlaybackCollection.ALBUM,
            PlaybackCollectionResolver.resolve(event(album = "Album", trackNumber = 2, totalTracks = 10)),
        )
    }

    @Test
    fun albumMetadataWithOnlyQueuePositionRemainsUnknown() {
        assertEquals(
            PlaybackCollection.UNKNOWN,
            PlaybackCollectionResolver.resolve(
                event(album = "Album", activeQueuePosition = 2, queueSize = 10),
            ),
        )
    }

    @Test
    fun queuePositionWithGenericNextTitleDoesNotPretendToBePlaylist() {
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
                )
            },
        )

        assertEquals(PlaybackCollection.ALBUM, PlaybackCollectionResolver.resolve(event))
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
    )
}
