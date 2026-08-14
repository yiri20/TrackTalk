package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementOutputPolicy
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppSettings
import com.trackvoice.data.CollectionFallback
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.UserSettings
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementPolicyTest {
    @Test
    fun appChecklistTakesPriorityOverLegacyAppMode() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                defaultMode = AnnouncementMode.TITLE_ONLY,
                useContentTypeSettings = false,
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
            ),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
                useCustomGuideSettings = true,
                mode = AnnouncementMode.TITLE_AND_ARTIST,
                readArtist = false,
                readCollection = false,
            ),
            externalAudioOutput = true,
        )
        assertTrue(decision.shouldAnnounce)
        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes.", decision.text)
    }

    @Test
    fun englishVoiceLanguageFlowsIntoPolicyGeneratedText() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                voiceLanguage = VoiceLanguage.ENGLISH,
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, Track 3, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun typeSpecificFieldsStayActiveWhenTheOldGlobalModeIsStillStored() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(defaultMode = AnnouncementMode.TITLE_ONLY, outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
                mode = AnnouncementMode.TITLE_AND_ARTIST,
            ),
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun smartUsesTrackNumberOnlyWhenAlbumContextIsPresent() {
        val smart = AnnouncementPolicy.decide(
            event(),
            UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            null,
            externalAudioOutput = true,
        )
        val noAlbum = AnnouncementPolicy.decide(
            event().copy(album = null),
            UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            null,
            externalAudioOutput = true,
        )
        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", smart.text)
        assertEquals("트랙 3번, Glass Eyes, Radiohead.", noAlbum.text)
    }

    @Test
    fun smartUsesPlaylistModeWhenQueueTitleIdentifiesPlaylist() {
        val decision = AnnouncementPolicy.decide(
            event().copy(queueTitle = "출근길 재생목록", queue = listOf(queueItem(), queueItem("Second"))),
            UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            null,
            externalAudioOutput = true,
        )
        assertEquals("재생목록 출근길 재생목록, A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun playlistChecklistCanReadAlbumAndTrackNumber() {
        val decision = AnnouncementPolicy.decide(
            event().copy(queueTitle = "출근길 재생목록"),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                playlistReadFields = setOf(
                    AnnouncementReadField.COLLECTION,
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TRACK_NUMBER,
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                ),
            ),
            null,
            externalAudioOutput = true,
        )

        assertEquals("재생목록 출근길 재생목록, A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun appFallbackCanResolveAnAmbiguousQueue() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(
                trackNumber = null,
                totalTracks = null,
                activeQueuePosition = 2,
                queue = List(11) { queueItem("Song $it") },
            ),
            userSettings = UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
                useCustomGuideSettings = true,
                collectionFallback = CollectionFallback.ALBUM,
            ),
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun smartUsesAlgorithmModeWhenQueueTitleIdentifiesAlgorithmicPlayback() {
        val decision = AnnouncementPolicy.decide(
            event().copy(queueTitle = "Daily Mix 1"),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                algorithmMode = AnnouncementMode.TITLE_ONLY,
                algorithmReadFields = setOf(AnnouncementReadField.TITLE),
            ),
            null,
            externalAudioOutput = true,
        )

        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun algorithmicDefaultIncludesSongAlbumAndTrackMetadata() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(queueTitle = "Daily Mix 1"),
            userSettings = UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun algorithmicChecklistCanReadAlbumAndTrackNumber() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(queueTitle = "Daily Mix 1"),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                algorithmMode = AnnouncementMode.ALBUM,
                algorithmReadFields = setOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TRACK_NUMBER,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun algorithmicChecklistCanReadOnlyAlbumName() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(queueTitle = "Daily Mix 1"),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                algorithmMode = AnnouncementMode.ALBUM,
                algorithmReadFields = setOf(AnnouncementReadField.ALBUM),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool.", decision.text)
    }

    @Test
    fun globalReadFieldsApplyToEveryCollectionWhenTypeSpecificSettingsAreOff() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                useContentTypeSettings = false,
                defaultReadFields = setOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun typeSpecificFieldsTakePriorityOverAnOldExplicitDefaultMode() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultMode = AnnouncementMode.TITLE_ONLY,
                useContentTypeSettings = true,
                defaultReadFields = setOf(AnnouncementReadField.TITLE),
                albumReadFields = setOf(
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TRACK_NUMBER,
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun albumChecklistCanOmitTrackNumber() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                albumReadFields = setOf(
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, Glass Eyes, Radiohead.", decision.text)
    }

    @Test
    fun albumChecklistReadsTitleAndTrackNumberWhenThoseAreTheOnlyCheckedFields() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                albumMode = AnnouncementMode.ALBUM,
                albumReadFields = setOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.TRACK_NUMBER,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("트랙 3번, Glass Eyes.", decision.text)
        assertTrue(decision.formatOptions.readTrackNumber)
        assertFalse(decision.formatOptions.readAlbum)
        assertFalse(decision.formatOptions.readArtist)
    }

    @Test
    fun albumAnnouncementRemainsEligibleWhileRequiredTitleIsTemporarilyMissing() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(title = null),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                albumMode = AnnouncementMode.ALBUM,
                albumReadFields = setOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.TRACK_NUMBER,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals("트랙 3번.", decision.text)
    }

    @Test
    fun announcementOrderFlowsFromPremiumSettingsIntoPolicy() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                announcementOrder = AnnouncementOrder.TRACK_NUMBER_FIRST,
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("트랙 3번, A Moon Shaped Pool, Glass Eyes, Radiohead.", decision.text)
        assertEquals(AnnouncementOrder.TRACK_NUMBER_FIRST, decision.formatOptions.announcementOrder)
    }

    @Test
    fun albumNameFirstTrackOnlySettingIsAppliedByPolicy() {
        val settings = UserSettings(
            outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
            albumNameFirstTrackOnly = true,
        )
        val first = AnnouncementPolicy.decide(
            event = event().copy(trackNumber = 1),
            userSettings = settings,
            appSettings = null,
            externalAudioOutput = true,
        )
        val second = AnnouncementPolicy.decide(
            event = event().copy(trackNumber = 2, title = "Second Song"),
            userSettings = settings,
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 1번, Glass Eyes, Radiohead.", first.text)
        assertEquals("트랙 2번, Second Song, Radiohead.", second.text)
    }

    @Test
    fun appChecklistCanReadAlbumTrackEvenWhenLegacyModeWasTitleAndArtist() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                albumMode = AnnouncementMode.TITLE_AND_ARTIST,
                useContentTypeSettings = false,
            ),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
                useCustomGuideSettings = true,
                mode = AnnouncementMode.SMART,
                readTitle = true,
                readArtist = false,
                readTrackNumber = true,
                readAlbum = false,
                readCollection = false,
            ),
            externalAudioOutput = true,
        )

        assertEquals("트랙 3번, Glass Eyes.", decision.text)
    }

    @Test
    fun albumChecklistUsesQueuePositionWhenTrackMetadataIsMissing() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(
                trackNumber = null,
                totalTracks = null,
                activeQueuePosition = 2,
                queue = List(11) { queueItem("Song $it", album = "A Moon Shaped Pool") },
            ),
            userSettings = UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, 트랙 3번, Glass Eyes, Radiohead.", decision.text)
    }

    private fun queueItem(title: String = "Glass Eyes", album: String? = null) = com.trackvoice.media.QueueItemSnapshot(
        mediaId = title,
        title = title,
        artist = "Radiohead",
        album = album,
    )

    @Test
    fun speakerCanBeSuppressedWithoutTouchingPlayback() {
        val decision = AnnouncementPolicy.decide(
            event(),
            UserSettings(outputPolicy = AnnouncementOutputPolicy.EXTERNAL_ONLY),
            null,
            externalAudioOutput = false,
        )
        assertFalse(decision.shouldAnnounce)
        assertEquals(AnnouncementSkipReason.SPEAKER_OUTPUT, decision.skipReason)
    }

    @Test
    fun allOutputsPolicyAllowsSpeakerAnnouncements() {
        val decision = AnnouncementPolicy.decide(
            event(),
            UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            null,
            externalAudioOutput = false,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(null, decision.skipReason)
    }

    @Test
    fun minimumPlaybackTimeDelaysAnnouncementInsteadOfDroppingIt() {
        val decision = AnnouncementPolicy.decide(
            event().copy(playbackPosition = 2_000L),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                timing = AnnouncementTiming.DELAYED,
                minimumPlaybackSeconds = 5,
            ),
            null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(3_000L, decision.delayMs)
    }

    @Test
    fun immediateTimingIgnoresMinimumPlaybackTime() {
        val decision = AnnouncementPolicy.decide(
            event().copy(playbackPosition = 2_000L),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                timing = AnnouncementTiming.IMMEDIATE,
                minimumPlaybackSeconds = 5,
            ),
            null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun announceBeforePlaybackIgnoresMinimumPlaybackTime() {
        val decision = AnnouncementPolicy.decide(
            event().copy(playbackPosition = 2_000L),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                trackStartBehavior = com.trackvoice.data.TrackStartBehavior.ANNOUNCE_THEN_PLAY,
                minimumPlaybackSeconds = 5,
            ),
            null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(0L, decision.delayMs)
    }

    private fun event() = PlaybackEvent(
        sourcePackageName = "com.youtube.music",
        sourceAppName = "YouTube Music",
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
        playbackPosition = 2_000L,
        observedAt = 1L,
        queue = emptyList(),
        // The policy fixtures that use this helper describe an actual album
        // queue. Direct-track classification is covered by the resolver tests
        // with no queue signal.
        queueTitle = "Album",
    )
}
