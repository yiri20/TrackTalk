package com.trackvoice.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreRepositoryInstrumentedTest {
    @Test
    fun appEnablementDefaultsAndExplicitChoicesSurviveRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val musicPackage = "com.trackvoice.test.category.music"
        val videoPackage = "com.trackvoice.test.category.video"
        val unknownPackage = "com.trackvoice.test.category.unknown"

        try {
            repository.removeApp(musicPackage)
            repository.removeApp(videoPackage)
            repository.removeApp(unknownPackage)

            repository.ensureApp(musicPackage, "Spotify")
            repository.ensureApp(videoPackage, "YouTube")
            repository.ensureApp(unknownPackage, "Unknown Player")

            val discovered = repository.currentAppSettings()
            assertTrue(discovered[musicPackage]!!.enabled)
            assertNull(discovered[musicPackage]!!.enabledOverride)
            assertFalse(discovered[videoPackage]!!.enabled)
            assertNull(discovered[videoPackage]!!.enabledOverride)
            assertFalse(discovered[unknownPackage]!!.enabled)

            // Updating another app setting must not turn an unset default into
            // an explicit false value.
            repository.updateAppSettings(discovered[unknownPackage]!!.copy(enabledOverride = null))
            repository.ensureApp(unknownPackage, "Spotify")
            assertTrue(repository.currentAppSettings()[unknownPackage]!!.enabled)

            repository.updateAppSettings(
                repository.currentAppSettings()[videoPackage]!!.copy(
                    enabled = true,
                    enabledOverride = true,
                ),
            )
            repository.updateAppSettings(
                repository.currentAppSettings()[musicPackage]!!.copy(
                    enabled = false,
                    enabledOverride = false,
                ),
            )

            val recreatedRepository = DataStoreRepository(context)
            val recreated = recreatedRepository.currentAppSettings()
            assertTrue(recreated[videoPackage]!!.enabled)
            assertEquals(true, recreated[videoPackage]!!.enabledOverride)
            assertFalse(recreated[musicPackage]!!.enabled)
            assertEquals(false, recreated[musicPackage]!!.enabledOverride)

            // An explicit choice remains authoritative even if the displayed
            // app/category evidence changes later.
            repository.ensureApp(musicPackage, "YouTube")
            assertFalse(repository.currentAppSettings()[musicPackage]!!.enabled)
            assertEquals(false, repository.currentAppSettings()[musicPackage]!!.enabledOverride)
        } finally {
            repository.removeApp(musicPackage)
            repository.removeApp(videoPackage)
            repository.removeApp(unknownPackage)
        }
    }

    @Test
    fun outputPolicySurvivesRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val original = repository.currentUserSettings()

        try {
            repository.updateUserSettings { current ->
                current.copy(outputPolicy = AnnouncementOutputPolicy.ALL_OUTPUTS)
            }

            val recreatedRepository = DataStoreRepository(context)
            assertEquals(
                AnnouncementOutputPolicy.ALL_OUTPUTS,
                recreatedRepository.currentUserSettings().outputPolicy,
            )
        } finally {
            repository.updateUserSettings { original }
        }
    }

    @Test
    fun orderedContentReadFieldsSurviveRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val original = repository.currentUserSettings()
        val selectedOrder = listOf(
            AnnouncementReadField.TRACK_NUMBER,
            AnnouncementReadField.TITLE,
        )

        try {
            repository.updateUserSettings { current ->
                current.copy(
                    albumReadFields = selectedOrder,
                    announcementOrder = AnnouncementOrder.DEFAULT,
                )
            }

            val recreatedRepository = DataStoreRepository(context)
            assertEquals(selectedOrder, recreatedRepository.currentUserSettings().albumReadFields)
        } finally {
            repository.updateUserSettings { original }
        }
    }

    @Test
    fun delayedReadingPersistsAUsableDelayAcrossRepositoryRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val original = repository.currentUserSettings()

        try {
            repository.updateUserSettings { current ->
                current.copy(
                    timing = AnnouncementTiming.DELAYED,
                    delaySeconds = 0,
                )
            }

            val recreated = DataStoreRepository(context).currentUserSettings()
            assertEquals(AnnouncementTiming.DELAYED, recreated.timing)
            assertEquals(AnnouncementTimingPolicy.MIN_DELAY_SECONDS, recreated.delaySeconds)
        } finally {
            repository.updateUserSettings { original }
        }
    }

    @Test
    fun legacyAppAnnouncementMigrationKeepsTheAppEligibilityOverride() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val packageName = "com.trackvoice.test.legacy.app"

        try {
            repository.removeApp(packageName)
            repository.ensureApp(packageName, "Legacy Player")
            repository.updateAppSettings(
                repository.currentAppSettings()[packageName]!!.copy(
                    enabled = false,
                    enabledOverride = false,
                ),
            )

            repository.migrateLegacyAppAnnouncementSettings()
            repository.migrateLegacyAppAnnouncementSettings()

            val migrated = DataStoreRepository(context).currentAppSettings()[packageName]!!
            assertFalse(migrated.enabled)
            assertEquals(false, migrated.enabledOverride)
        } finally {
            repository.removeApp(packageName)
        }
    }

    @Test
    fun persistedAnnouncementSurvivesRepositoryRecreationAndCanBeCleared() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = DataStoreRepository(context)
        val announcement = PersistedAnnouncement(
            sourcePackageName = "com.example.player",
            sourceAppName = "Example Player",
            title = "Track A",
            artist = "Artist A",
            album = "Album A",
            trackNumber = 3,
            discNumber = 1,
            duration = 180_000L,
            mediaId = "track-a",
            trackNumberReliable = true,
            trackNumberSource = "MEDIA_METADATA",
            announcedAt = 123_456L,
        )

        try {
            repository.savePersistedAnnouncement(announcement)

            assertEquals(announcement, DataStoreRepository(context).currentPersistedAnnouncement())

            repository.clearPersistedAnnouncement()
            assertNull(DataStoreRepository(context).currentPersistedAnnouncement())
        } finally {
            repository.clearPersistedAnnouncement()
        }
    }
}
