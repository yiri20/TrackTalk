package com.trackvoice.announcement

import com.trackvoice.data.AnnouncementMode
import com.trackvoice.data.AnnouncementOrder
import com.trackvoice.data.AnnouncementOutputPolicy
import com.trackvoice.data.AnnouncementReadField
import com.trackvoice.data.AnnouncementTiming
import com.trackvoice.data.AppSettings
import com.trackvoice.data.VoiceLanguage
import com.trackvoice.data.UserSettings
import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackCollection
import com.trackvoice.media.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementPolicyTest {
    @Test
    fun contentSpecificOffUsesOnlyTheDefaultAnnouncementSource() {
        val configuration = AnnouncementPolicy.resolveConfiguration(
            userSettings = UserSettings(useContentTypeSettings = false),
            collection = PlaybackCollection.ALBUM,
        )

        assertEquals(AnnouncementConfigurationSource.DEFAULT, configuration.source)
    }

    @Test
    fun betaRuntimeOmitsStoredTrackNumberUntilMetadataSupportIsReady() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TRACK_NUMBER,
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, Glass Eyes, Radiohead.", decision.text)
        assertFalse(decision.formatOptions.readTrackNumber)
        assertFalse(decision.formatOptions.orderedFields.orEmpty().contains(AnnouncementReadField.TRACK_NUMBER))
    }

    @Test
    fun playbackContextNeverChangesTheGlobalAnnouncementSource() {
        val configuration = AnnouncementPolicy.resolveConfiguration(
            userSettings = UserSettings(useContentTypeSettings = true),
            collection = PlaybackCollection.ALBUM,
        )

        assertEquals(AnnouncementConfigurationSource.DEFAULT, configuration.source)
        assertEquals(PlaybackCollection.UNKNOWN, configuration.collection)
    }

    @Test
    fun appEnablementDoesNotBecomeAnAnnouncementConfigurationSource() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = AppSettings("com.youtube.music", "YouTube Music", enabled = true),
            externalAudioOutput = true,
        )

        assertEquals(PlaybackCollection.ALBUM, decision.collection)
        assertEquals(AnnouncementConfigurationSource.DEFAULT, AnnouncementPolicy.resolveConfiguration(UserSettings(), decision.collection).source)
    }

    @Test
    fun runtimePolicyBlocksAnAppThatIsDisabledByItsEffectiveEnablement() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = AppSettings(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                enabled = false,
            ),
            effectiveEnabled = true,
            externalAudioOutput = true,
        )

        assertFalse(decision.shouldAnnounce)
        assertEquals(AnnouncementSkipReason.APP_DISABLED, decision.skipReason)
    }

    @Test
    fun appSettingsOnlyControlEligibilityAndCannotOverrideDefaultFields() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                defaultMode = AnnouncementMode.TITLE_ONLY,
                useContentTypeSettings = false,
                defaultReadFields = listOf(AnnouncementReadField.TITLE),
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
            ),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
            ),
            externalAudioOutput = true,
        )
        assertTrue(decision.shouldAnnounce)
        assertEquals("Glass Eyes.", decision.text)
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

        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun typeSpecificFieldsStayActiveWithoutPerAppConfiguration() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(defaultMode = AnnouncementMode.TITLE_ONLY, outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
            ),
            externalAudioOutput = true,
        )

        assertEquals("Glass Eyes.", decision.text)
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
        assertEquals("Glass Eyes.", smart.text)
        assertEquals("Glass Eyes.", noAlbum.text)
    }

    @Test
    fun queueTitleDoesNotChangeGlobalReadingFields() {
        val decision = AnnouncementPolicy.decide(
            event().copy(queueTitle = "출근길 재생목록", queue = listOf(queueItem(), queueItem("Second"))),
            UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            null,
            externalAudioOutput = true,
        )
        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun playlistChecklistCanReadAlbumAndTrackNumber() {
        val decision = AnnouncementPolicy.decide(
            event().copy(queueTitle = "출근길 재생목록"),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                playlistReadFields = listOf(
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

        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun ambiguousQueueDoesNotUseLegacyPerAppFallback() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(
                queueTitle = null,
                trackNumber = null,
                totalTracks = null,
                activeQueuePosition = null,
                queue = emptyList(),
            ),
            userSettings = UserSettings(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
            ),
            externalAudioOutput = true,
        )

        assertEquals(PlaybackCollection.UNKNOWN, decision.collection)
    }

    @Test
    fun queueTypeDoesNotChangeGlobalReadingFields() {
        val decision = AnnouncementPolicy.decide(
            event().copy(queueTitle = "Daily Mix 1"),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                algorithmMode = AnnouncementMode.TITLE_ONLY,
                algorithmReadFields = listOf(AnnouncementReadField.TITLE),
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

        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun globalChecklistCanReadAlbumAndTrackNumber() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(queueTitle = "Daily Mix 1"),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TRACK_NUMBER,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("Glass Eyes, Radiohead, A Moon Shaped Pool.", decision.text)
    }

    @Test
    fun globalChecklistCanReadOnlyAlbumName() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(queueTitle = "Daily Mix 1"),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(AnnouncementReadField.ALBUM),
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
                defaultReadFields = listOf(
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
    fun globalFieldsTakePriorityOverAnOldExplicitDefaultMode() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultMode = AnnouncementMode.TITLE_ONLY,
                useContentTypeSettings = true,
                defaultReadFields = listOf(
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TRACK_NUMBER,
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
    fun globalChecklistCanOmitTrackNumber() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(
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
    fun globalChecklistReadsTitleAndTrackNumberWhenThoseAreTheOnlyCheckedFields() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.TRACK_NUMBER,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("Glass Eyes.", decision.text)
        assertFalse(decision.formatOptions.readTrackNumber)
        assertFalse(decision.formatOptions.readAlbum)
        assertFalse(decision.formatOptions.readArtist)
    }

    @Test
    fun emptyGlobalSelectionFallsBackToAReadableAnnouncement() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = emptyList(),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun globalAnnouncementRemainsEligibleWhileRequiredTitleIsTemporarilyMissing() {
        val decision = AnnouncementPolicy.decide(
            event = event().copy(title = null),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.TRACK_NUMBER,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertFalse(decision.shouldAnnounce)
        assertEquals(AnnouncementSkipReason.NO_TEXT, decision.skipReason)
    }

    @Test
    fun dragOrderFlowsDirectlyIntoPolicy() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                defaultReadFields = listOf(
                    AnnouncementReadField.TRACK_NUMBER,
                    AnnouncementReadField.ALBUM,
                    AnnouncementReadField.TITLE,
                    AnnouncementReadField.ARTIST,
                ),
            ),
            appSettings = null,
            externalAudioOutput = true,
        )

        assertEquals("A Moon Shaped Pool, Glass Eyes, Radiohead.", decision.text)
        assertEquals(AnnouncementOrder.DEFAULT, decision.formatOptions.announcementOrder)
    }

    @Test
    fun albumNameFirstTrackOnlyDoesNotDependOnUnreliablePlaybackContext() {
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

        assertEquals("Glass Eyes.", first.text)
        assertEquals("Second Song.", second.text)
    }

    @Test
    fun appSettingsCannotOverrideGlobalReadFields() {
        val decision = AnnouncementPolicy.decide(
            event = event(),
            userSettings = UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                useContentTypeSettings = false,
                defaultReadFields = listOf(
                    AnnouncementReadField.TRACK_NUMBER,
                    AnnouncementReadField.TITLE,
                ),
            ),
            appSettings = AppSettings(
                "com.youtube.music",
                "YouTube Music",
            ),
            externalAudioOutput = true,
        )

        assertEquals("Glass Eyes.", decision.text)
    }

    @Test
    fun globalChecklistOmitsTrackWhenTrackMetadataIsMissing() {
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

        assertEquals("Glass Eyes.", decision.text)
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
    fun delayedTimingUsesThePersistedAnnouncementDelay() {
        val decision = AnnouncementPolicy.decide(
            event().copy(playbackPosition = 0L),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                timing = AnnouncementTiming.DELAYED,
                delaySeconds = 2,
            ),
            null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(2_000L, decision.delayMs)
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

    @Test
    fun announceBeforePlaybackStillUsesDelayedTiming() {
        val decision = AnnouncementPolicy.decide(
            event().copy(playbackPosition = 0L),
            UserSettings(
                outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS,
                timing = AnnouncementTiming.DELAYED,
                delaySeconds = 2,
                trackStartBehavior = com.trackvoice.data.TrackStartBehavior.ANNOUNCE_THEN_PLAY,
                minimumPlaybackSeconds = 30,
            ),
            null,
            externalAudioOutput = true,
        )

        assertTrue(decision.shouldAnnounce)
        assertEquals(2_000L, decision.delayMs)
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
