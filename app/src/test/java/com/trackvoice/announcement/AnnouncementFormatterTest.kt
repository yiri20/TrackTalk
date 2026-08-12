package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
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
            "앨범 A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(event(), AnnouncementMode.ALBUM),
        )
    }

    @Test
    fun englishVoiceLanguageLocalizesAlbumAndTrackLabels() {
        assertEquals(
            "Album A Moon Shaped Pool, Track 3, Glass Eyes, Radiohead.",
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
            "앨범 A Moon Shaped Pool, Glass Eyes, Radiohead.",
            AnnouncementFormatter.format(event(trackNumber = null), AnnouncementMode.ALBUM),
        )
    }

    @Test
    fun albumModeUsesQueuePositionWhenTrackMetadataIsMissing() {
        assertEquals(
            "앨범 A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.",
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
                collection = PlaybackCollection.PLAYLIST,
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
