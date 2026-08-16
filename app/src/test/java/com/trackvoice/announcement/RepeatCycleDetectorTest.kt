package com.trackvoice.announcement

import com.trackvoice.media.PlaybackEvent
import com.trackvoice.media.PlaybackStatus
import com.trackvoice.media.RepeatMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatCycleDetectorTest {
    @Test
    fun endToStartWithRepeatOneIsANewCycle() {
        val previous = event(position = 179_000L, repeatMode = RepeatMode.ONE)
        val current = event(position = 0L, repeatMode = RepeatMode.ONE)

        assertTrue(RepeatCycleDetector.isNewRepeatOneCycle(previous, current))
    }

    @Test
    fun seekToStartIsNotARepeatCycleWithoutRepeatOne() {
        val previous = event(position = 179_000L, repeatMode = RepeatMode.NONE)
        val current = event(position = 0L, repeatMode = RepeatMode.NONE)

        assertFalse(RepeatCycleDetector.isNewRepeatOneCycle(previous, current))
    }

    @Test
    fun smallBackwardSeekIsNotARepeatCycle() {
        val previous = event(position = 40_000L, repeatMode = RepeatMode.ONE)
        val current = event(position = 0L, repeatMode = RepeatMode.ONE)

        assertFalse(RepeatCycleDetector.isNewRepeatOneCycle(previous, current))
    }

    @Test
    fun differentTrackIsNotARepeatCycle() {
        val previous = event(title = "A", position = 179_000L, repeatMode = RepeatMode.ONE)
        val current = event(title = "B", position = 0L, repeatMode = RepeatMode.ONE)

        assertFalse(RepeatCycleDetector.isNewRepeatOneCycle(previous, current))
    }

    private fun event(
        title: String = "A",
        position: Long,
        repeatMode: RepeatMode,
    ) = PlaybackEvent(
        sourcePackageName = "com.spotify.music",
        sourceAppName = "Spotify",
        title = title,
        artist = "Artist",
        album = "Album",
        albumArtist = "Artist",
        trackNumber = 1,
        totalTracks = 10,
        discNumber = 1,
        duration = 180_000L,
        mediaId = "track-$title",
        playbackState = PlaybackStatus.PLAYING,
        playbackPosition = position,
        observedAt = position,
        repeatMode = repeatMode,
    )
}
