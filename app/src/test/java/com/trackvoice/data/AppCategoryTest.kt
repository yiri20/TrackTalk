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
    fun onlyMusicStreamingAppsAreEnabledByDefault() {
        assertTrue(defaultAppGuideEnabled("com.spotify.music", "Spotify"))
        assertFalse(defaultAppGuideEnabled("com.google.android.youtube", "YouTube"))
        assertFalse(defaultAppGuideEnabled("com.audible.application", "Audible"))
        assertFalse(defaultAppGuideEnabled("com.podcastaddict", "Podcast Addict"))
        assertFalse(defaultAppGuideEnabled("com.example.unknown", "Unknown Player"))
    }

    @Test
    fun explicitAppChoiceOverridesCategoryDefault() {
        assertFalse(
            AppGuideEnablementPolicy.effectiveEnabled(
                "com.spotify.music",
                "Spotify",
                explicitOverride = false,
            ),
        )
        assertTrue(
            AppGuideEnablementPolicy.effectiveEnabled(
                YOUTUBE_PACKAGE_NAME,
                "YouTube",
                explicitOverride = true,
            ),
        )
    }

    @Test
    fun categoryCorrectionChangesOnlyAppsWithoutAnOverride() {
        assertFalse(
            AppGuideEnablementPolicy.effectiveEnabled(
                "com.example.player",
                "Unknown Player",
                explicitOverride = null,
            ),
        )
        assertTrue(
            AppGuideEnablementPolicy.effectiveEnabled(
                "com.example.player",
                "Spotify",
                explicitOverride = null,
            ),
        )
        assertFalse(
            AppGuideEnablementPolicy.effectiveEnabled(
                "com.example.player",
                "Spotify",
                explicitOverride = false,
            ),
        )
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
