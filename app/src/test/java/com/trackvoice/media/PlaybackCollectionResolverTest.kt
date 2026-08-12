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
    fun albumMetadataWithQueuePositionIdentifiesAlbumWhenTrackMetadataIsMissing() {
        assertEquals(
            PlaybackCollection.ALBUM,
            PlaybackCollectionResolver.resolve(
                event(album = "Album", activeQueuePosition = 2, queueSize = 10),
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
