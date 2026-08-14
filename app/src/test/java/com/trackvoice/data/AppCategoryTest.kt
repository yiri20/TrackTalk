package com.trackvoice.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class AppCategoryTest {
    @Test
    fun youtubeGuideIsOffByDefaultButYoutubeMusicIsNot() {
        assertFalse(defaultAppGuideEnabled(YOUTUBE_PACKAGE_NAME))
        assertTrue(defaultAppGuideEnabled("com.google.android.apps.youtube.music"))
    }

    @Test
    fun youtubeMusicIsMusicStreaming() {
        assertEquals(
            AppCategory.MUSIC_STREAMING,
            categorizeApp("com.google.android.apps.youtube.music", "YouTube Music"),
        )
    }

    @Test
    fun youtubeIsMusicVideo() {
        assertEquals(
            AppCategory.MUSIC_VIDEO,
            categorizeApp("com.google.android.youtube", "YouTube"),
        )
    }

    @Test
    fun explicitMusicVideoAppDoesNotBecomeMusicStreaming() {
        assertEquals(
            AppCategory.MUSIC_VIDEO,
            categorizeApp("com.example.musicvideoplayer", "Music Video Player"),
        )
    }

    @Test
    fun learningAppIsLearning() {
        assertEquals(
            AppCategory.LEARNING,
            categorizeApp("com.udemy.android", "Udemy"),
        )
    }

    @Test
    fun podcastAppIsPodcast() {
        assertEquals(
            AppCategory.PODCAST,
            categorizeApp("com.podcastaddict", "Podcast Addict"),
        )
    }

    @Test
    fun unknownMediaAppFallsBackToOther() {
        assertEquals(
            AppCategory.OTHER,
            categorizeApp("com.example.player", "차량 플레이어"),
        )
    }
}
