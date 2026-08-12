package com.trackvoice.media

import android.media.MediaMetadata
import android.media.MediaDescription
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackMetadataMapperInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        session = MediaSession(context, "TrackVoiceInstrumentationTest")
        session.isActive = true
    }

    @After
    fun tearDown() {
        session.release()
    }

    @Test
    fun mapsMediaSessionMetadataPlaybackAndQueue() {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Glass Eyes")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Radiohead")
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "A Moon Shaped Pool")
                .putString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST, "Radiohead")
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "track-3")
                .putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, 3L)
                .putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, 11L)
                .putLong(MediaMetadata.METADATA_KEY_DISC_NUMBER, 1L)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, 180_000L)
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PLAYING, 12_345L, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .build(),
        )
        session.setQueue(
            listOf(
                MediaSession.QueueItem(
                    MediaDescription.Builder()
                        .setMediaId("track-3")
                        .setTitle("Glass Eyes")
                        .setSubtitle("Radiohead")
                        .build(),
                    3L,
                ),
            ),
        )

        val controller = MediaController(context, session.sessionToken)
        val event = TrackMetadataMapper { "Test Music" }.map(controller, observedAt = 123L)

        assertEquals("com.trackvoice", event.sourcePackageName)
        assertEquals("Test Music", event.sourceAppName)
        assertEquals("Glass Eyes", event.title)
        assertEquals("Radiohead", event.artist)
        assertEquals("A Moon Shaped Pool", event.album)
        assertEquals(3, event.trackNumber)
        assertEquals(11, event.totalTracks)
        assertEquals(1, event.discNumber)
        assertEquals(180_000L, event.duration)
        assertEquals("track-3", event.mediaId)
        assertEquals(PlaybackStatus.PLAYING, event.playbackState)
        assertTrue("playing position should not move backwards", event.playbackPosition!! >= 12_345L)
        assertTrue("playing position should remain close to the seeded value", event.playbackPosition!! < 20_000L)
        assertEquals(123L, event.observedAt)
        assertEquals(1, event.queue.size)
        assertEquals("Glass Eyes", event.queue.single().title)
        assertEquals("Radiohead", event.queue.single().artist)
    }

    @Test
    fun missingMetadataProducesEmptyOptionalFields() {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f)
                .build(),
        )

        val controller = MediaController(context, session.sessionToken)
        val event = TrackMetadataMapper { "Test Music" }.map(controller, observedAt = 456L)

        assertEquals("com.trackvoice", event.sourcePackageName)
        assertEquals(PlaybackStatus.PAUSED, event.playbackState)
        assertEquals(456L, event.observedAt)
        assertEquals(null, event.title)
        assertEquals(null, event.artist)
        assertEquals(null, event.trackNumber)
        assertEquals(null, event.duration)
        assertNotNull(event.queue)
        assertEquals(0, event.queue.size)
    }

    @Test
    fun usesActiveQueueDescriptionWhenMetadataIsMissing() {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(PlaybackState.STATE_PAUSED, 0L, 0f)
                .setActiveQueueItemId(7L)
                .build(),
        )
        session.setQueue(
            listOf(
                MediaSession.QueueItem(
                    MediaDescription.Builder()
                        .setMediaId("queue-track-7")
                        .setTitle("Queue Song")
                        .setSubtitle("Queue Artist")
                        .build(),
                    7L,
                ),
            ),
        )

        val controller = MediaController(context, session.sessionToken)
        val event = TrackMetadataMapper { "Test Music" }.map(controller, observedAt = 789L)

        assertEquals("Queue Song", event.title)
        assertEquals("Queue Artist", event.artist)
        assertEquals("queue-track-7", event.mediaId)
    }
}
