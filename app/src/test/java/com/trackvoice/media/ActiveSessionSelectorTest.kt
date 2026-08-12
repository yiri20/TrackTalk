package com.trackvoice.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveSessionSelectorTest {
    @Test
    fun playingSessionWinsOverMoreRecentlyChangedPausedSession() {
        val playing = snapshot(
            key = "spotify",
            status = PlaybackStatus.PLAYING,
            lastObservedAt = 10L,
            lastMetadataChangedAt = 10L,
        )
        val paused = snapshot(
            key = "youtube",
            status = PlaybackStatus.PAUSED,
            lastObservedAt = 100L,
            lastMetadataChangedAt = 100L,
        )

        assertEquals("spotify", ActiveSessionSelector.select(listOf(paused, playing))?.sessionKey)
    }

    @Test
    fun mostRecentlyObservedPlayingSessionWins() {
        val older = snapshot(
            key = "spotify",
            status = PlaybackStatus.PLAYING,
            lastObservedAt = 20L,
            lastMetadataChangedAt = 20L,
        )
        val newer = snapshot(
            key = "youtube",
            status = PlaybackStatus.PLAYING,
            lastObservedAt = 50L,
            lastMetadataChangedAt = 40L,
        )

        assertEquals("youtube", ActiveSessionSelector.select(listOf(older, newer))?.sessionKey)
    }

    @Test
    fun mediaKeyPlayingSessionWinsOverStalePlayingSession() {
        val stale = snapshot(
            key = "spotify",
            status = PlaybackStatus.PLAYING,
            lastObservedAt = 100L,
            lastMetadataChangedAt = 100L,
        )
        val mediaKey = snapshot(
            key = "youtube",
            status = PlaybackStatus.PLAYING,
            isMediaKeySession = true,
            lastObservedAt = 20L,
            lastMetadataChangedAt = 20L,
        )

        assertEquals("youtube", ActiveSessionSelector.select(listOf(stale, mediaKey))?.sessionKey)
    }

    @Test
    fun mediaKeySessionIsFallbackWhenNothingIsPlaying() {
        val mediaKey = snapshot(
            key = "spotify",
            status = PlaybackStatus.PAUSED,
            isMediaKeySession = true,
            lastObservedAt = 10L,
            lastMetadataChangedAt = 10L,
        )
        val stale = snapshot(
            key = "youtube",
            status = PlaybackStatus.PAUSED,
            lastObservedAt = 100L,
            lastMetadataChangedAt = 100L,
        )

        assertEquals("spotify", ActiveSessionSelector.select(listOf(stale, mediaKey))?.sessionKey)
    }

    @Test
    fun sessionsWithoutTitlesAreIgnored() {
        val noTitle = snapshot(key = "empty", title = null, status = PlaybackStatus.PLAYING)

        assertNull(ActiveSessionSelector.select(listOf(noTitle)))
    }

    private fun snapshot(
        key: String,
        title: String? = "Glass Eyes",
        status: PlaybackStatus,
        isMediaKeySession: Boolean = false,
        lastObservedAt: Long = 1L,
        lastMetadataChangedAt: Long = 1L,
    ) = SessionSnapshot(
        sessionKey = key,
        event = PlaybackEvent(
            sourcePackageName = "com.$key",
            sourceAppName = key,
            title = title,
            artist = "Radiohead",
            album = "A Moon Shaped Pool",
            albumArtist = "Radiohead",
            trackNumber = 3,
            totalTracks = 11,
            discNumber = 1,
            duration = 180_000L,
            mediaId = "$key-track-3",
            playbackState = status,
            playbackPosition = 0L,
            observedAt = lastObservedAt,
        ),
        isMediaKeySession = isMediaKeySession,
        lastMetadataChangedAt = lastMetadataChangedAt,
        lastPlaybackStateChangedAt = lastObservedAt,
        lastObservedAt = lastObservedAt,
    )
}
