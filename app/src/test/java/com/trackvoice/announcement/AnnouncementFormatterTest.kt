package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.data.VoiceLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementFormatterTest {
    @Test
    fun orderedFieldsAreSpokenInTheSameOrderAsTheSelection() {
        assertEquals(
            "트랙 3번, Glass Eyes, A Moon Shaped Pool, Radiohead.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(
                    orderedFields = listOf(
                        AnnouncementReadField.TRACK_NUMBER,
                        AnnouncementReadField.TITLE,
                        AnnouncementReadField.ALBUM,
                        AnnouncementReadField.ARTIST,
                    ),
                ),
                collection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun titleOnlyReadsTitle() {
        assertEquals("Glass Eyes.", AnnouncementFormatter.format(event(), AnnouncementMode.TITLE_ONLY))
    }

    @Test
    fun testTextUsesSelectedVoiceLanguage() {
        assertEquals("Track 3, Glass Eyes. Radiohead.", AnnouncementFormatter.testText(VoiceLanguage.ENGLISH))
        assertEquals("트랙 3번, Glass Eyes. Radiohead.", AnnouncementFormatter.testText(VoiceLanguage.KOREAN))
    }

    @Test
    fun titleAndArtistReadsBoth() {
        assertEquals("Glass Eyes, Radiohead.", AnnouncementFormatter.format(event(), AnnouncementMode.TITLE_AND_ARTIST))
    }

    @Test
    fun albumModeReadsTrackAndTitle() {
        assertEquals(
            "A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(event(), AnnouncementMode.ALBUM),
        )
    }

    @Test
    fun plusOrderCanPutTitleFirst() {
        assertEquals(
            "Glass Eyes, A Moon Shaped Pool, 트랙 3번, Radiohead.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(announcementOrder = AnnouncementOrder.TITLE_FIRST),
                collection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun plusOrderCanPutTrackNumberFirst() {
        assertEquals(
            "트랙 3번, A Moon Shaped Pool, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(announcementOrder = AnnouncementOrder.TRACK_NUMBER_FIRST),
                collection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun plusOrderSkipsAnUnavailableFieldAndKeepsCheckedFields() {
        assertEquals(
            "Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(
                    readAlbum = false,
                    readTrackNumber = false,
                    announcementOrder = AnnouncementOrder.ALBUM_FIRST,
                ),
                collection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun plusOrderCanPutPlaylistNameFirst() {
        assertEquals(
            "Glass Eyes, 재생목록 출근길, A Moon Shaped Pool, 트랙 3번, Radiohead.",
            AnnouncementFormatter.format(
                event(queueTitle = "출근길"),
                AnnouncementMode.PLAYLIST,
                AnnouncementFormatOptions(announcementOrder = AnnouncementOrder.TITLE_FIRST),
                collection = PlaybackCollection.PLAYLIST,
            ),
        )
    }

    @Test
    fun englishVoiceLanguageLocalizesAlbumAndTrackLabels() {
        assertEquals(
            "A Moon Shaped Pool, Track 3, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                voiceLanguage = VoiceLanguage.ENGLISH,
            ),
        )
    }

    @Test
    fun nullTrackFallsBackToTitle() {
        assertEquals(
            "A Moon Shaped Pool, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(event(trackNumber = null), AnnouncementMode.ALBUM),
        )
    }

    @Test
    fun albumModeUsesQueuePositionWhenTrackMetadataIsMissing() {
        assertEquals(
            "A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(trackNumber = null).copy(
                    activeQueuePosition = 2,
                    queue = List(11) { com.trackvoice.media.QueueItemSnapshot("track-$it", "Song $it", "Artist") },
                ),
                AnnouncementMode.ALBUM,
                collection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun albumModeDoesNotUseQueuePositionAfterQueueOrderChanges() {
        val announcement = AnnouncementFormatter.format(
            event(trackNumber = null).copy(
                activeQueuePosition = 0,
                queue = List(11) { com.trackvoice.media.QueueItemSnapshot("track-$it", "Song $it", "Artist") },
                queueOrderChanged = true,
            ),
            AnnouncementMode.ALBUM,
            collection = PlaybackCollection.ALBUM,
        )

        assertFalse(announcement.orEmpty().contains("1"))
    }

    @Test
    fun albumModeDoesNotReadUnreliableMetadataTrackAfterShuffle() {
        val announcement = AnnouncementFormatter.format(
            event(trackNumber = 1).copy(
                activeQueuePosition = 0,
                queueOrderChanged = true,
                trackNumberReliable = false,
            ),
            AnnouncementMode.ALBUM,
            collection = PlaybackCollection.ALBUM,
        )

        assertFalse(announcement.orEmpty().contains("1"))
    }

    @Test
    fun playlistModeReadsCollectionTitleAndArtist() {
        assertEquals(
            "재생목록 출근길, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(queueTitle = "출근길"),
                AnnouncementMode.PLAYLIST,
                AnnouncementFormatOptions(
                    readAlbum = false,
                    readTrackNumber = false,
                ),
                collection = PlaybackCollection.PLAYLIST,
            ),
        )
    }

    @Test
    fun playlistModeCanReadAlbumAndTrackNumberWhenSelected() {
        assertEquals(
            "재생목록 출근길, A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(queueTitle = "출근길"),
                AnnouncementMode.PLAYLIST,
                AnnouncementFormatOptions(
                    readTitle = true,
                    readArtist = true,
                    readTrackNumber = true,
                    readAlbum = true,
                    readCollection = true,
                ),
                collection = PlaybackCollection.PLAYLIST,
            ),
        )
    }

    @Test
    fun algorithmicModeCanReadAlbumAndTrackWhenSelected() {
        assertEquals(
            "A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(
                    readTitle = true,
                    readArtist = true,
                    readTrackNumber = true,
                    readAlbum = true,
                    readCollection = false,
                ),
                collection = PlaybackCollection.ALGORITHMIC,
            ),
        )
    }

    @Test
    fun albumOnlyReadsTheAlbumNameWithoutARedundantLabel() {
        assertEquals(
            "A Moon Shaped Pool.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(
                    readTitle = false,
                    readArtist = false,
                    readTrackNumber = false,
                    readAlbum = true,
                    readCollection = false,
                ),
                collection = PlaybackCollection.ALGORITHMIC,
            ),
        )
    }

    @Test
    fun albumNameCanBeLimitedToTheFirstTrack() {
        val options = AnnouncementFormatOptions(albumNameFirstTrackOnly = true)
        assertEquals(
            "A Moon Shaped Pool, 트랙 1번, First Song, Radiohead.",
            AnnouncementFormatter.format(
                event(title = "First Song", trackNumber = 1),
                AnnouncementMode.ALBUM,
                options,
                collection = PlaybackCollection.ALBUM,
            ),
        )
        assertEquals(
            "트랙 2번, Second Song, Radiohead.",
            AnnouncementFormatter.format(
                event(title = "Second Song", trackNumber = 2),
                AnnouncementMode.ALBUM,
                options,
                collection = PlaybackCollection.ALBUM,
            ),
        )
    }

    @Test
    fun albumNameFirstTrackOnlyUsesStableQueuePositionWhenTrackMetadataIsMissing() {
        val options = AnnouncementFormatOptions(albumNameFirstTrackOnly = true)
        val first = event(trackNumber = null).copy(activeQueuePosition = 0)
        val second = event(title = "Second Song", trackNumber = null).copy(activeQueuePosition = 1)

        assertEquals(
            "A Moon Shaped Pool, 트랙 1번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(first, AnnouncementMode.ALBUM, options, PlaybackCollection.ALBUM),
        )
        assertEquals(
            "트랙 2번, Second Song, Radiohead.",
            AnnouncementFormatter.format(second, AnnouncementMode.ALBUM, options, PlaybackCollection.ALBUM),
        )
    }

    @Test
    fun albumNameFirstTrackOnlyDoesNotGuessFirstTrackAfterQueueReorder() {
        val announcement = AnnouncementFormatter.format(
            event(trackNumber = null).copy(activeQueuePosition = 0, queueOrderChanged = true),
            AnnouncementMode.ALBUM,
            AnnouncementFormatOptions(albumNameFirstTrackOnly = true),
            PlaybackCollection.ALBUM,
        )

        assertEquals("Glass Eyes, Radiohead.", announcement)
    }

    @Test
    fun albumNameFirstTrackOnlyDoesNotTreatTrackOneAsFirstDuringShuffle() {
        val announcement = AnnouncementFormatter.format(
            event(trackNumber = 1).copy(shuffleState = com.trackvoice.media.ShuffleState.ON),
            AnnouncementMode.ALBUM,
            AnnouncementFormatOptions(albumNameFirstTrackOnly = true),
            PlaybackCollection.ALBUM,
        )

        assertEquals("트랙 1번, Glass Eyes, Radiohead.", announcement)
    }

    @Test
    fun trackNumberOnlyReadsTheDirectTrackNumber() {
        assertEquals(
            "트랙 3번.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(
                    readTitle = false,
                    readArtist = false,
                    readTrackNumber = true,
                    readAlbum = false,
                    readCollection = false,
                ),
                collection = PlaybackCollection.ALGORITHMIC,
            ),
        )
    }

    @Test
    fun algorithmicModeDoesNotTreatQueuePositionAsAlbumTrackNumber() {
        val announcement = AnnouncementFormatter.format(
            event(trackNumber = null).copy(
                activeQueuePosition = 2,
                queue = List(11) { com.trackvoice.media.QueueItemSnapshot("track-$it", "Song $it", "Artist") },
            ),
            AnnouncementMode.ALBUM,
            AnnouncementFormatOptions(
                readTitle = true,
                readArtist = true,
                readTrackNumber = true,
                readAlbum = true,
                readCollection = false,
            ),
            collection = PlaybackCollection.ALGORITHMIC,
        )

        assertEquals("A Moon Shaped Pool, Glass Eyes, Radiohead.", announcement)
    }

    @Test
    fun nonAlbumPlaybackCanReadDirectAlbumTrackMetadata() {
        assertEquals(
            "A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(queueTitle = "Daily Mix 1"),
                AnnouncementMode.ALBUM,
                collection = PlaybackCollection.ALGORITHMIC,
            ),
        )
    }

    @Test
    fun englishVoiceLanguageLocalizesPlaylistLabel() {
        assertEquals(
            "Playlist 출근길, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(
                event(queueTitle = "출근길"),
                AnnouncementMode.PLAYLIST,
                AnnouncementFormatOptions(
                    readAlbum = false,
                    readTrackNumber = false,
                ),
                collection = PlaybackCollection.PLAYLIST,
                voiceLanguage = VoiceLanguage.ENGLISH,
            ),
        )
    }

    @Test
    fun readOptionsCanSuppressAlbumAndArtistWithoutSuppressingTitle() {
        assertEquals(
            "트랙 3번, Glass Eyes.",
            AnnouncementFormatter.format(
                event(),
                AnnouncementMode.ALBUM,
                AnnouncementFormatOptions(readArtist = false, readAlbum = false),
            ),
        )
    }

    @Test
    fun missingTitleProducesNoAnnouncement() {
        assertNull(AnnouncementFormatter.format(event(title = null), AnnouncementMode.TITLE_ONLY))
    }

    private fun event(
        title: String? = "Glass Eyes",
        trackNumber: Int? = 3,
        queueTitle: String? = null,
    ) = PlaybackEvent(
        sourcePackageName = "com.spotify.music",
        sourceAppName = "Spotify",
        title = title,
        artist = "Radiohead",
        album = "A Moon Shaped Pool",
        albumArtist = "Radiohead",
        trackNumber = trackNumber,
        totalTracks = 11,
        discNumber = 1,
        duration = 180_000L,
        mediaId = "track-3",
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = 0L,
        observedAt = 1L,
        queueTitle = queueTitle,
    )
}
