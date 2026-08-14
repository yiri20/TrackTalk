package com.trackvoice.media

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.trackvoice.service.TrackVoiceNotificationListenerService
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaSessionMonitorInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var session: MediaSession

    @Before
    fun setUp() {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val trackTalkListener = ComponentName(
            context,
            TrackVoiceNotificationListenerService::class.java,
        ).flattenToString()
        assumeTrue(
            "TrackTalk notification access is required for MediaSessionMonitor integration tests",
            enabledListeners.split(':').any { it == trackTalkListener },
        )

        session = MediaSession(context, "TrackTalkMonitorInstrumentationTest")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPause() {
                // Simulate a media app that publishes PAUSED after its command
                // callback. This is the race that used to lose auto-resume.
                mainHandler.postDelayed({ setState(PlaybackState.STATE_PAUSED) }, 350L)
            }

            override fun onPlay() {
                setState(PlaybackState.STATE_PLAYING)
            }
        }, mainHandler)
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Delayed Resume Test")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "TrackTalk")
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "delayed-resume-test")
                .build(),
        )
        session.setQueueTitle("Test playlist")
        setState(PlaybackState.STATE_PLAYING)
        session.isActive = true
    }

    @After
    fun tearDown() {
        if (::session.isInitialized) session.release()
        mainHandler.removeCallbacksAndMessages(null)
    }

    @Test
    fun resumesWhenMediaAppPublishesPauseAfterResumeWasRequested() {
        val monitor = MediaSessionMonitor(context) {}
        monitor.start()
        try {
            waitUntil { monitor.isSelectedPlaybackPlaying() == true }

            val token = monitor.pauseSelectedIfPlaying()
            assertNotNull("The active test session should be paused", token)
            monitor.resumePlayback(token!!)

            waitUntil(timeoutMs = 2_500L) {
                MediaController(context, session.sessionToken).playbackState?.state == PlaybackState.STATE_PLAYING
            }
            assertTrue(monitor.isSelectedPlaybackPlaying() == true)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun resumesWhenMediaIdTemporarilyDisappearsDuringMetadataRefresh() {
        val monitor = MediaSessionMonitor(context) {}
        monitor.start()
        try {
            waitUntil { monitor.isSelectedPlaybackPlaying() == true }

            val token = monitor.pauseSelectedIfPlaying()
            assertNotNull("The active test session should be paused", token)
            // Several real media apps clear MEDIA_ID for one callback while
            // keeping the visible title/artist unchanged.
            session.setMetadata(
                MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, "Delayed Resume Test")
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, "TrackTalk")
                    .build(),
            )
            monitor.resumePlayback(token!!)

            waitUntil(timeoutMs = 2_500L) {
                MediaController(context, session.sessionToken).playbackState?.state == PlaybackState.STATE_PLAYING
            }
            assertTrue(monitor.isSelectedPlaybackPlaying() == true)
        } finally {
            monitor.stop()
        }
    }

    private fun setState(state: Int) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, 0L, 1f)
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .build(),
        )
    }

    private fun waitUntil(timeoutMs: Long = 1_500L, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(40L)
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }
}
